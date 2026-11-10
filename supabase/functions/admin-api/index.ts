import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
};

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "GET" && req.method !== "POST") {
    return Response.json({ error: "Method not allowed" }, { status: 405, headers: cors });
  }

  const authorization = req.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) {
    return Response.json({ error: "Authentication required" }, { status: 401, headers: cors });
  }

  const url = Deno.env.get("SUPABASE_URL");
  const anon = Deno.env.get("SUPABASE_ANON_KEY");
  const service = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !anon || !service) {
    return Response.json({ error: "Server configuration is incomplete" }, { status: 500, headers: cors });
  }

  const caller = createClient(url, anon, {
    auth: { persistSession: false, autoRefreshToken: false },
    global: { headers: { Authorization: authorization } },
  });
  const { data: userData, error: userError } = await caller.auth.getUser();
  if (userError || !userData.user) {
    return Response.json({ error: "Invalid session" }, { status: 401, headers: cors });
  }

  // Betalink's sole initial administrator is pinned server-side to this exact verified account.
  // The Android client cannot grant itself admin privileges or override this allowlist.
  if ((userData.user.email ?? "").trim().toLowerCase() !== "hz.yazilimm@gmail.com" || !userData.user.email_confirmed_at) {
    return Response.json({ error: "Verified owner account required" }, { status: 403, headers: cors });
  }

  const admin = createClient(url, service, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  if (req.method === "GET") {
    const [users, campaigns, reports, groups, pending, support, userRows, groupRows] = await Promise.all([
      admin.from("profiles").select("user_id", { count: "exact", head: true }),
      admin.from("campaigns").select("id", { count: "exact", head: true }),
      admin.from("reports").select("id", { count: "exact", head: true }),
      admin.from("tester_groups").select("id", { count: "exact", head: true }),
      admin.from("reports").select("id,reporter_id,target_user_id,campaign_id,reason,details,status,created_at").eq("status", "pending").order("created_at", { ascending: false }).limit(50),
      admin.from("support_tickets").select("id,user_id,subject,message,status,ticket_type,created_at").in("status", ["open","in_progress"]).order("created_at", { ascending: false }).limit(50),
      admin.from("profiles").select("user_id,display_name").order("created_at", { ascending: false }).limit(500),
      admin.from("tester_groups").select("id,name,description,created_by,tracking_status,created_at").order("created_at", { ascending: false }).limit(200),
    ]);
    if (pending.error || support.error || userRows.error || groupRows.error) return Response.json({ error: "Could not load admin data" }, { status: 500, headers: cors });

    // Auth emails are private account data: fetch them only inside this owner-verified
    // service-role function. Never expose them through the public profiles table.
    const authUsers: Array<{ id: string; email?: string }> = [];
    for (let page = 1; page <= 100; page++) {
      const { data, error } = await admin.auth.admin.listUsers({ page, perPage: 1000 });
      if (error) return Response.json({ error: "Could not load account email addresses" }, { status: 500, headers: cors });
      authUsers.push(...data.users.map((u) => ({ id: u.id, email: u.email ?? "" })));
      if (data.users.length < 1000) break;
    }
    const emailByUserId = new Map(authUsers.map((u) => [u.id, u.email]));
    const usersList = (userRows.data ?? []).map((row) => ({
      user_id: row.user_id,
      display_name: row.display_name,
      email: emailByUserId.get(row.user_id) ?? "",
    }));
    const supportTickets = (support.data ?? []).map((row) => ({
      ...row,
      user_email: emailByUserId.get(row.user_id) ?? "",
    }));

    return Response.json({
      stats: {
        users: users.count ?? 0,
        campaigns: campaigns.count ?? 0,
        reports: reports.count ?? 0,
        groups: groups.count ?? 0,
      },
      pendingReports: pending.data ?? [],
      supportTickets,
      usersList,
      groupsList: groupRows.data ?? [],
    }, { headers: cors });
  }

  let payload: Record<string, unknown>;
  try { payload = await req.json(); } catch { return Response.json({ error: "Invalid JSON body" }, { status: 400, headers: cors }); }

  const action = payload.action;

  if (action === "adjust_bp") {
    const amount = Number(payload.amount);
    const reason = String(payload.reason ?? "").trim();
    const userIds = Array.isArray(payload.user_ids) ? [...new Set(payload.user_ids.map(String))] : [];
    if (!Number.isInteger(amount) || amount === 0 || Math.abs(amount) > 100000 || reason.length < 3 || reason.length > 240 || userIds.length < 1 || userIds.length > 500) {
      return Response.json({ error: "Invalid BP adjustment" }, { status: 400, headers: cors });
    }
    const { data: targets, error: targetError } = await admin.from("profiles").select("user_id").in("user_id", userIds);
    if (targetError || (targets ?? []).length !== userIds.length) return Response.json({ error: "One or more users were not found" }, { status: 404, headers: cors });
    for (const target of targets ?? []) {
      const { error: walletError } = await admin.from("betalink_wallets").upsert({ user_id: target.user_id }, { onConflict: "user_id", ignoreDuplicates: true });
      if (walletError) return Response.json({ error: "Wallet initialization failed" }, { status: 500, headers: cors });
      const { data: wallet, error: readError } = await admin.from("betalink_wallets").select("balance,lifetime_earned").eq("user_id", target.user_id).single();
      if (readError || !wallet || wallet.balance + amount < 0) return Response.json({ error: "Adjustment would make a wallet negative" }, { status: 409, headers: cors });
      const { error: updateError } = await admin.from("betalink_wallets").update({
        balance: wallet.balance + amount,
        lifetime_earned: wallet.lifetime_earned + Math.max(0, amount),
        updated_at: new Date().toISOString(),
      }).eq("user_id", target.user_id);
      if (updateError) return Response.json({ error: "Wallet update failed" }, { status: 500, headers: cors });
      const { error: ledgerError } = await admin.from("betalink_wallet_ledger").insert({
        user_id: target.user_id, amount, entry_type: "admin_adjustment", description: reason,
      });
      if (ledgerError) return Response.json({ error: "Wallet ledger write failed" }, { status: 500, headers: cors });
    }
    await admin.from("admin_audit_logs").insert({
      actor_id: userData.user.id, action: "adjust_bp", target_type: "users",
      target_id: null, metadata: { user_ids: userIds, amount, reason },
    });
    return Response.json({ success: true, affected: userIds.length }, { headers: cors });
  }

  if (action === "create_group") {
    const name = String(payload.name ?? "").trim();
    const description = String(payload.description ?? "").trim();
    if (name.length < 2 || name.length > 80 || description.length > 1000) return Response.json({ error: "Invalid group details" }, { status: 400, headers: cors });
    const { data: group, error } = await admin.from("tester_groups").insert({
      name, description, is_private: false, created_by: userData.user.id,
    }).select("id").single();
    if (error || !group) return Response.json({ error: "Group creation failed" }, { status: 500, headers: cors });
    await admin.from("group_members").upsert({ group_id: group.id, user_id: userData.user.id, member_role: "leader", status: "approved" }, { onConflict: "group_id,user_id" });
    await admin.from("admin_audit_logs").insert({ actor_id: userData.user.id, action: "create_group", target_type: "group", target_id: group.id, metadata: { name } });
    return Response.json({ success: true, group_id: group.id }, { headers: cors });
  }

  if (action === "delete_group") {
    const groupId = String(payload.group_id ?? "");
    if (!groupId) return Response.json({ error: "Group id required" }, { status: 400, headers: cors });
    const { data: group } = await admin.from("tester_groups").select("id,name").eq("id", groupId).maybeSingle();
    if (!group) return Response.json({ error: "Group not found" }, { status: 404, headers: cors });
    const { error } = await admin.from("tester_groups").delete().eq("id", groupId);
    if (error) return Response.json({ error: "Group deletion failed" }, { status: 500, headers: cors });
    await admin.from("admin_audit_logs").insert({ actor_id: userData.user.id, action: "delete_group", target_type: "group", target_id: groupId, metadata: { name: group.name } });
    return Response.json({ success: true }, { headers: cors });
  }

  if (action === "resolve_report") {
    const reportId = String(payload.report_id ?? "");
    const status = String(payload.status ?? "");
    if (!reportId || !["resolved", "rejected", "reviewing"].includes(status)) {
      return Response.json({ error: "Invalid report action" }, { status: 400, headers: cors });
    }
    const { error } = await admin.from("reports").update({ status }).eq("id", reportId);
    if (error) return Response.json({ error: "Report update failed" }, { status: 500, headers: cors });
    await admin.from("admin_audit_logs").insert({
      actor_id: userData.user.id, action: "resolve_report", target_type: "report",
      target_id: reportId, metadata: { status },
    });
    return Response.json({ success: true }, { headers: cors });
  }

  if (action === "block_user") {
    const targetId = String(payload.user_id ?? "");
    if (!targetId || targetId === userData.user.id) {
      return Response.json({ error: "Invalid target user" }, { status: 400, headers: cors });
    }
    const { error: banError } = await admin.auth.admin.updateUserById(targetId, { ban_duration: "876000h" });
    if (banError) return Response.json({ error: "Account suspension failed" }, { status: 500, headers: cors });
    const { error } = await admin.from("blocked_users").upsert({
      blocker_id: userData.user.id, blocked_id: targetId,
    }, { onConflict: "blocker_id,blocked_id" });
    if (error) return Response.json({ error: "User block record failed" }, { status: 500, headers: cors });
    await admin.from("admin_audit_logs").insert({
      actor_id: userData.user.id, action: "block_user", target_type: "user",
      target_id: targetId, metadata: {},
    });
    return Response.json({ success: true }, { headers: cors });
  }

  if (action === "resolve_support_ticket") {
    const ticketId = String(payload.ticket_id ?? "");
    const reply = String(payload.reply ?? "").trim();
    if (!ticketId || reply.length < 2 || reply.length > 5000) {
      return Response.json({ error: "Invalid support response" }, { status: 400, headers: cors });
    }
    const { error } = await admin.from("support_tickets")
      .update({ status: "resolved", admin_reply: reply, updated_at: new Date().toISOString() })
      .eq("id", ticketId);
    if (error) return Response.json({ error: "Support ticket update failed" }, { status: 500, headers: cors });
    await admin.from("admin_audit_logs").insert({
      actor_id: userData.user.id, action: "resolve_support_ticket", target_type: "support_ticket",
      target_id: ticketId, metadata: {},
    });
    return Response.json({ success: true }, { headers: cors });
  }

  if (action === "announce") {
    const title = String(payload.title ?? "").trim();
    const body = String(payload.body ?? "").trim();
    if (title.length < 2 || body.length < 2 || title.length > 120 || body.length > 2000) {
      return Response.json({ error: "Announcement title or body is invalid" }, { status: 400, headers: cors });
    }
    const { data: recipients, error: recipientError } = await admin.from("profiles").select("user_id");
    if (recipientError) return Response.json({ error: "Could not load recipients" }, { status: 500, headers: cors });
    const rows = (recipients ?? []).map((row) => ({
      recipient_id: row.user_id, notification_type: "announcement", title, body,
    }));
    if (rows.length > 0) {
      const { error } = await admin.from("notifications").insert(rows);
      if (error) return Response.json({ error: "Announcement delivery failed" }, { status: 500, headers: cors });
    }
    await admin.from("admin_audit_logs").insert({
      actor_id: userData.user.id, action: "announce", target_type: "platform",
      target_id: null, metadata: { recipient_count: rows.length, title },
    });
    return Response.json({ success: true, recipient_count: rows.length }, { headers: cors });
  }

  return Response.json({ error: "Unsupported action" }, { status: 400, headers: cors });
});
