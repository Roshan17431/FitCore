import { useContext, useState } from 'react';
import { AuthContext } from '../../context/auth-context';
import Button from './Button';

export function FreshContextGuidance() {
  return <div role="alert" className="mb-4 space-y-3 rounded-xl border border-line bg-surface-alt p-4 text-sm leading-6">
    <p>This browser cannot prove the interrupted session. This original context remains blocked.</p>
    <p>Continue in a separate browser profile, or close all private windows and start a new private session. A new tab or another private window in an existing private session can share the same cookies.</p>
    <p>Open FitCore in that separate context and sign in explicitly. For verification or recovery, open the original email link there. Success there does not repair or release this original context.</p>
  </div>;
}

// Public account receipts require PREAUTH. An authenticated view must explicitly
// sign out, and an interrupted writer must be proven before any account request.
export default function CookieSessionNotice() {
  const auth = useContext(AuthContext), [busy, setBusy] = useState(false);
  if (!auth.cookieTransport) return null;
  const act = async action => { if (busy) return; setBusy(true); try { await action(); } finally { setBusy(false); } };
  if (auth.sessionFreshContextRequired) return <FreshContextGuidance />;
  return <div className="mb-4 space-y-3">
    {auth.sessionError ? <p role="alert" className="text-sm">{auth.sessionError}</p> : null}
    {auth.loading ? <p role="status" className="text-sm">Checking browser session…</p> : null}
    {auth.user || auth.sessionSignOutRequired ? <><p className="text-sm">Sign out the previous session before continuing with this account request.</p><Button type="button" disabled={busy} onClick={() => act(auth.logout)}>Sign out previous session</Button></> : auth.sessionRecoveryAvailable ? <Button type="button" disabled={busy} onClick={() => act(auth.recoverSession)}>Check sign-in status</Button> : null}
  </div>;
}
