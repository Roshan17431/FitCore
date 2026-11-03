package com.fitcore.gym.security;

import com.fitcore.gym.email.EmailTemplates;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EmailTemplatesTest {
    private static final String TOKEN = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";
    private static String link(String kind) {
        return "https://app.fixture.invalid" + switch (kind) {
            case "RESET" -> "/reset-password#token=";
            case "MEMBER_INVITE", "TRAINER_INVITE" -> "/secure/register#invitationToken=";
            default -> "/verify-email#token=";
        } + TOKEN;
    }

    @Test void everyKindHasBrandedHtmlAndPlainText() {
        for (String kind : new String[]{"OWNER", "VERIFY", "MEMBER_INVITE", "TRAINER_INVITE", "RESET", "PASSWORD_CHANGED"}) {
            var result = EmailTemplates.render(kind, "Fixture Gym", kind.equals("PASSWORD_CHANGED") ? "" : link(kind), "30 minutes");
            assertThat(result.subject()).contains("FitCore").doesNotContain("Fixture Gym", "\r", "\n");
            assertThat(result.text()).contains("FitCore", "Fixture Gym");
            assertThat(result.html()).contains("FitCore", "Fixture Gym", "<table", "viewport", "style=");
            if (!kind.equals("PASSWORD_CHANGED")) {
                assertThat(result.text()).contains(link(kind), "30 minutes", "ignore", "share");
                assertThat(result.html()).contains(link(kind), "30 minutes");
            } else {
                assertThat(result.text()).contains("password", "contact");
            }
        }
    }

    @Test void interpolatedTextIsEscapedAndSubjectsStayStatic() {
        var result = EmailTemplates.render("VERIFY", "<img src=x onerror='bad()'> & \"Gym\"", link("VERIFY"), "<b>30 minutes</b>");
        assertThat(result.html()).doesNotContain("<img", "<b>30").contains("&lt;img", "&#39;", "&amp;", "&quot;", "&lt;b&gt;30");
        assertThat(result.subject()).isEqualTo(EmailTemplates.render("VERIFY", "Other Gym", link("VERIFY"), "1 hour").subject());
    }

    @Test void rejectsUnsafeOrMisroutedLinks() {
        String good = link("VERIFY");
        for (String bad : new String[]{
                "javascript:alert(1)", "http://app.fixture.invalid/verify-email#token=" + TOKEN,
                "https://user:pass@app.fixture.invalid/verify-email#token=" + TOKEN,
                good + "&redirect=https://evil.invalid", good.replace("/verify-email", "/reset-password"),
                good.replace("/verify-email", "/%76erify-email"), good.replace("#", "?next=https://evil.invalid#"),
                good.replace(TOKEN, "short"), good + "\r\nBcc: other@example.invalid",
                "//app.fixture.invalid/verify-email#token=" + TOKEN,
                "https://app.fixture.invalid/verify-email#token=" + TOKEN + "%0A",
                "https://app.fixture.invalid/verify-email#invitationToken=" + TOKEN}) {
            assertThatThrownBy(() -> EmailTemplates.render("VERIFY", "Gym", bad, "30 minutes"))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining(TOKEN);
        }
    }

    @Test void acceptsExactLoopbackHttpForLocalDevelopment() {
        for (String host : new String[]{"localhost", "127.0.0.1", "[::1]"}) {
            String local = "http://" + host + ":5173/verify-email#token=" + TOKEN;
            assertThat(EmailTemplates.render("VERIFY", "Gym", local, "30 minutes").text()).contains(local);
        }
        assertThatThrownBy(() -> EmailTemplates.render("VERIFY", "Gym", "http://localhost.evil.invalid/verify-email#token=" + TOKEN, "30 minutes"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void requiresExpiryAndRejectsControlCharactersAndUnknownKinds() {
        assertThatThrownBy(() -> EmailTemplates.render("VERIFY", "Gym", link("VERIFY"), " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EmailTemplates.render("VERIFY", "Gym\r\nBcc: bad", link("VERIFY"), "1 hour")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EmailTemplates.render("OTHER", "Gym", link("VERIFY"), "1 hour")).isInstanceOf(IllegalArgumentException.class);
        assertThat(EmailTemplates.render("PASSWORD_CHANGED", "Gym", null, null).html()).doesNotContain("href=");
        assertThatThrownBy(() -> EmailTemplates.render("PASSWORD_CHANGED", "Gym", "https://evil.invalid", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void recordStringRepresentationNeverIncludesPayload() {
        var result = EmailTemplates.render("RESET", "Private Gym", link("RESET"), "30 minutes");
        assertThat(result.toString()).doesNotContain(TOKEN, "Private Gym", "30 minutes").contains("redacted");
    }
}
