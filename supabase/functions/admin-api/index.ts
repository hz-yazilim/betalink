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

  const admin = createClient(url, service, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: grant, error: grantError } = await admin
    .from("platform_admins")
    .select("user_id")
    .eq("user_id", userData.user.id)
    .maybeSingle();

  if (grantError || !grant) {
    return Response.json({ error: "Administrator access required" }, { status: 403, headers: cors });
  }

  if (req.method === "GET") {
    const [users, campaigns, reports, groups, pending, support] = await Promise.all([
      admin.from("profiles").select("user_id", { count: "exact", head: true }),
      admin.from("campaigns").select("id", { count: "exact", head: true }),
      admin.from("reports").select("id", { count: "exact", head: true }),
      admin.from("tester_groups").select("id", { count: "exact", head: true }),
      admin.from("reports").select("id,reporter_id,target_user_id,campaign_id,reason,details,status,created_at").eq("status", "pending").order("created_at", { ascending: false }).limit(50),
      admin.from("support_tickets").select("id,user_id,subject,message,status,created_at").in("status", ["open","in_progress"]).order("created_at", { ascending: false }).limit(50),
    ]);
    if (pending.error || support.error) return Response.json({ error: "Could not load moderation queue" }, { status: 500, headers: cors });
    return Response.json({
      stats: {
        users: users.count ?? 0,
        campaigns: campaigns.count ?? 0,
        reports: reports.count ?? 0,
        groups: groups.count ?? 0,
      },
      pendingReports: pending.data ?? [],
      supportTickets: support.data ?? [],
    }, { headers: cors });
  }

  let payload: Record<string, unknown>;
  try { payload = await req.json(); } catch { return Response.json({ error: "Invalid JSON body" }, { status: 400, headers: cors }); }

  const action = payload.action;
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
