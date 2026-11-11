import { useContext, useEffect, useRef, useState } from 'react';
import Card from '../components/common/Card';
import Button from '../components/common/Button';
import Modal from '../components/common/Modal';
import PageHeader from '../components/common/PageHeader';
import LoadingSpinner from '../components/common/LoadingSpinner';
import FormField from '../components/forms/FormField';
import { gymService, formatApiError } from '../services/api';
import { AuthContext } from '../context/auth-context';
const utcDate = value => new Date(/(?:Z|[+-]\d{2}:\d{2})$/.test(value) ? value : `${value}Z`);
const status = invite => invite.acceptedAt ? 'Accepted' : invite.revokedAt ? 'Revoked' : utcDate(invite.expiresAt) <= new Date() ? 'Expired' : 'Pending';
export function InvitationReceipt({ receipt }) {
  return <div role="status" className="space-y-2 rounded-xl border border-line bg-surface-alt p-4 text-sm">
    <p className="font-semibold">Invitation queued</p>
    <p>This confirms queue intent, not provider acceptance or mailbox delivery.</p>
    <p>Reference: {receipt.id} · {receipt.role} · Expires {utcDate(receipt.expiresAt).toLocaleString()} (local time)</p>
  </div>;
}
export default function Invitations() {
  const { cookieTransport } = useContext(AuthContext);
  const [receipt, setReceipt] = useState(null);
  const [rows, setRows] = useState(null), [email, setEmail] = useState(''), [role, setRole] = useState('MEMBER');
  const [error, setError] = useState(''), [busy, setBusy] = useState(false), [secret, setSecret] = useState(null), [revoke, setRevoke] = useState(null), [copied, setCopied] = useState(false);
  const lock = useRef(false);
  useEffect(() => { let active = true; gymService.invitations().then(data => { if (active) setRows(data); }).catch(e => { if (active) setError(formatApiError(e)); }); return () => { active = false; }; }, []);
  const issue = async event => { event.preventDefault(); if (lock.current) return; lock.current = true; setBusy(true); setError(''); setSecret(null); setReceipt(null); try { const issued = await gymService.invite({ email: email.trim(), role }); if (cookieTransport) setReceipt(issued); else setSecret(`${window.location.origin}/register#invitationToken=${issued.invitationToken}`); setCopied(false); setEmail(''); setRows(await gymService.invitations()); } catch (e) { setError(e.response?.data?.message === 'DEVELOPMENT_ONBOARDING_DISABLED' ? 'Invitations are unavailable in this environment. Demo onboarding is disabled.' : formatApiError(e)); } finally { lock.current = false; setBusy(false); } };
  const confirm = async () => { if (lock.current || !revoke) return; lock.current = true; setBusy(true); setError(''); try { await gymService.revoke(revoke.id); setRows(await gymService.invitations()); setRevoke(null); } catch (e) { setError(formatApiError(e)); } finally { lock.current = false; setBusy(false); } };
  return <div className="space-y-6"><PageHeader title="Invitations" description="Bring members and trainers into your gym securely." />
    {error ? <p role="alert" className="rounded-xl border border-line bg-surface p-4">{error}</p> : null}
    <Card><h2 className="text-lg font-semibold">Invite someone to your gym</h2><p className="mb-5 mt-2 text-sm text-muted">{cookieTransport ? 'Request an invitation email. A queue receipt does not confirm provider acceptance or mailbox delivery.' : 'Local demo links. Delivery is disabled by default; this screen does not confirm email delivery.'}</p><form className="grid items-end gap-4 md:grid-cols-[1fr_12rem_auto]" onSubmit={issue}><FormField required maxLength={254} type="email" label="Invitation email" value={email} onChange={e => setEmail(e.target.value)} /><FormField label="Invitation role" options={['MEMBER', 'TRAINER']} value={role} onChange={e => setRole(e.target.value)} /><Button type="submit" isLoading={busy}>Create invitation</Button></form></Card>
    {receipt ? <InvitationReceipt receipt={receipt} /> : null}
    <Card><h2 className="mb-4 text-lg font-semibold">Invitation history</h2>{rows === null ? <LoadingSpinner label="Loading invitations" /> : rows.length === 0 ? <p className="py-8 text-center text-sm text-muted">No invitations yet. Your first invitation will appear here.</p> : <ul className="divide-y divide-line">{rows.map(row => <li key={row.id} className="flex flex-col justify-between gap-4 py-4 sm:flex-row sm:items-center"><div className="min-w-0"><p className="break-all font-semibold">{row.email}</p><p className="mt-1 text-xs text-muted">{row.role} · {status(row)} · Expires {utcDate(row.expiresAt).toLocaleString()} (local time)</p></div>{status(row) === 'Pending' ? <Button variant="outline" disabled={busy} onClick={() => setRevoke(row)}>Revoke invitation</Button> : null}</li>)}</ul>}</Card>
    <Modal isOpen={!cookieTransport && Boolean(secret)} onClose={() => setSecret(null)} title="Invitation link ready"><p className="mb-4 text-sm leading-6 text-muted">Copy this link now. It will not be shown again. Share it privately with the invited person. Email delivery is not confirmed here.</p><FormField label="One-time invitation link" readOnly value={secret || ''} onFocus={e => e.target.select()} /><div className="mt-4 flex flex-wrap gap-3"><Button onClick={async () => { try { await navigator.clipboard.writeText(secret); setCopied(true); } catch { setCopied(false); setError('Clipboard unavailable. Select and copy the link manually.'); } }}>{copied ? 'Copied' : 'Copy invitation link'}</Button><Button variant="outline" onClick={() => setSecret(null)}>Done</Button></div></Modal>
    <Modal isOpen={Boolean(revoke)} onClose={() => { if (!busy) setRevoke(null); }} title="Revoke invitation?"><p className="mb-5 break-words text-sm">The unused link for {revoke?.email} will stop working. Existing accounts will remain unchanged.</p><div className="flex gap-3"><Button variant="danger" isLoading={busy} onClick={confirm}>Confirm revocation</Button><Button variant="outline" disabled={busy} onClick={() => setRevoke(null)}>Cancel</Button></div></Modal>
  </div>;
}
