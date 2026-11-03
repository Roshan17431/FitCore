package com.fitcore.gym.email;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/** Pure transactional templates. The caller supplies its configured, approved application origin. */
public final class EmailTemplates {
    private EmailTemplates() {}

    public record Rendered(String subject, String text, String html) {
        @Override public String toString() { return "Rendered[subject=redacted, text=redacted, html=redacted]"; }
    }

    public static Rendered render(String kind, String gymName, String link, String expiresDescription) {
        if (kind == null) throw invalid();
        String gym = requiredText(gymName);
        String subject;
        String title;
        String message;
        String action;
        String path;
        switch (kind) {
            case "OWNER" -> {
                subject = "Welcome to FitCore — verify your email";
                title = "Your gym starts here";
                message = "Welcome to FitCore! Verify your email to finish setting up " + gym + ".";
                action = "Verify email"; path = "/verify-email";
            }
            case "VERIFY" -> {
                subject = "Verify your FitCore email";
                title = "Verify your email";
                message = "Confirm your email address for " + gym + " on FitCore.";
                action = "Verify email"; path = "/verify-email";
            }
            case "MEMBER_INVITE", "TRAINER_INVITE" -> {
                boolean trainer = kind.equals("TRAINER_INVITE");
                subject = trainer ? "Your FitCore trainer invitation" : "Your FitCore member invitation";
                title = "You are invited";
                message = "You have been invited to join " + gym + " on FitCore as a " + (trainer ? "trainer" : "member") + ".";
                action = "Accept invitation"; path = "/secure/register";
            }
            case "RESET" -> {
                subject = "Reset your FitCore password";
                title = "Reset your password";
                message = "We received a request to reset the password for your FitCore account at " + gym + ".";
                action = "Reset password"; path = "/reset-password";
            }
            case "PASSWORD_CHANGED" -> {
                subject = "Your FitCore password was changed";
                title = "Password changed";
                message = "The password for your FitCore account at " + gym + " was changed.";
                action = null; path = null;
            }
            default -> throw invalid();
        }

        String expiry = "";
        String guidance;
        if (path == null) {
            if (link != null && !link.isEmpty()) throw invalid();
            guidance = "If you did not make this change, contact your gym administrator immediately to secure your account. Never share your password or security codes.";
        } else {
            validateLink(link, path);
            expiry = "This link expires in " + requiredText(expiresDescription) + ". It can only be used once.";
            guidance = "If you did not request this email or do not recognize this gym, you can ignore it. Never share this link; it grants access to your account. If you are concerned, contact your gym administrator.";
        }
        String text = "FitCore\n\n" + title + "\n\n" + message + "\n\n"
                + (path == null ? "" : action + ": " + link + "\n\n" + expiry + "\n\n")
                + guidance + "\n\nFitCore • Your gym, connected.\n";
        String button = path == null ? "" : "<p style=\"margin:28px 0;\"><a href=\"" + escape(link)
                + "\" style=\"display:inline-block;padding:14px 24px;background:#176b50;color:#ffffff;text-decoration:none;border-radius:8px;font-weight:bold;\">"
                + escape(action) + "</a></p><p style=\"font-size:14px;color:#475569;\">" + escape(expiry)
                + "</p><p style=\"font-size:13px;color:#475569;overflow-wrap:anywhere;word-break:break-all;\">If the button does not work, copy this link into your browser:<br><a style=\"color:#176b50;\" href=\""
                + escape(link) + "\">" + escape(link) + "</a></p>";
        String html = "<!doctype html><html lang=\"en\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>"
                + escape(subject) + "</title></head><body style=\"margin:0;padding:0;background:#f1f5f4;font-family:Arial,Helvetica,sans-serif;color:#172b26;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"width:100%;background:#f1f5f4;\"><tr><td align=\"center\" style=\"padding:28px 12px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"width:100%;max-width:600px;background:#ffffff;border-radius:12px;\">"
                + "<tr><td style=\"padding:28px 24px;background:#123c30;color:#ffffff;border-radius:12px 12px 0 0;font-size:24px;font-weight:bold;\">FitCore</td></tr>"
                + "<tr><td style=\"padding:28px 24px;font-size:16px;line-height:1.6;\"><h1 style=\"margin:0 0 20px;font-size:26px;line-height:1.25;\">"
                + escape(title) + "</h1><p>" + escape(message) + "</p>" + button
                + "<p style=\"margin-top:28px;padding-top:20px;border-top:1px solid #e2e8f0;font-size:14px;color:#475569;\">"
                + escape(guidance) + "</p></td></tr></table><p style=\"font-size:12px;color:#64748b;\">FitCore • Your gym, connected.</p>"
                + "</td></tr></table></body></html>";
        return new Rendered(subject, text, html);
    }

    private static String requiredText(String value) {
        if (value == null || value.isBlank() || value.length() > 1000
                || value.codePoints().anyMatch(Character::isISOControl)) throw invalid();
        return value;
    }

    private static void validateLink(String link, String expectedPath) {
        if (link == null || link.length() > 2048 || link.codePoints().anyMatch(Character::isISOControl)) throw invalid();
        try {
            URI uri = new URI(link);
            String host = uri.getHost();
            boolean local = Set.of("localhost", "127.0.0.1", "[::1]").contains(host == null ? "" : host);
            boolean schemeAllowed = "https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && local);
            String fragmentKey = expectedPath.equals("/secure/register") ? "invitationToken" : "token";
            if (!schemeAllowed || host == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getPort() > 65535 || !expectedPath.equals(uri.getRawPath())
                    || uri.getRawFragment() == null || !uri.getRawFragment().matches(fragmentKey + "=[A-Za-z0-9_-]{43}")) throw invalid();
        } catch (URISyntaxException ex) {
            throw invalid();
        }
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid transactional email template input");
    }
}
