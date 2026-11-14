import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") {
    return Response.json({ error: "Method not allowed" }, { status: 405, headers: corsHeaders });
  }

  const authorization = req.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) {
    return Response.json({ error: "Authentication required" }, { status: 401, headers: corsHeaders });
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !anonKey || !serviceRoleKey) {
    return Response.json({ error: "Server configuration is incomplete" }, { status: 500, headers: corsHeaders });
  }

  const userClient = createClient(supabaseUrl, anonKey, {
    auth: { persistSession: false, autoRefreshToken: false },
    global: { headers: { Authorization: authorization } },
  });
  const { data: userData, error: userError } = await userClient.auth.getUser();
  if (userError || !userData.user) {
    return Response.json({ error: "Invalid or expired session" }, { status: 401, headers: corsHeaders });
  }

  const adminClient = createClient(supabaseUrl, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  // Remove only this verified user's public profile images before deleting the Auth identity.
  // If cleanup fails, stop here so the user can retry instead of leaving orphaned images.
  let offset = 0;
  while (true) {
    const { data: files, error: listError } = await adminClient.storage
      .from("profile-avatars")
      .list(userData.user.id, { limit: 100, offset, sortBy: { column: "name", order: "asc" } });
    if (listError) {
      return Response.json({ error: "Profile image cleanup failed; account was not deleted." }, { status: 500, headers: corsHeaders });
    }
    const paths = (files ?? [])
      .filter((file) => file.id !== null)
      .map((file) => userData.user.id + "/" + file.name);
    for (let i = 0; i < paths.length; i += 100) {
      const { error: removeError } = await adminClient.storage.from("profile-avatars").remove(paths.slice(i, i + 100));
      if (removeError) {
        return Response.json({ error: "Profile image cleanup failed; account was not deleted." }, { status: 500, headers: corsHeaders });
      }
    }
    if ((files ?? []).length < 100) break;
    offset += (files ?? []).length;
  }

  const { error: deleteError } = await adminClient.auth.admin.deleteUser(userData.user.id);
  if (deleteError) {
    return Response.json({ error: "Account deletion failed" }, { status: 500, headers: corsHeaders });
  }

  return Response.json({ success: true }, { status: 200, headers: corsHeaders });
});
