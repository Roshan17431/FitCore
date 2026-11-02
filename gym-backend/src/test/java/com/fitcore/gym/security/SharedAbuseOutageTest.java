package com.fitcore.gym.security;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.time.Clock;
class SharedAbuseOutageTest {
 @Test void storeFailureBlocksEverySecurityFlow()throws Exception {
  var store=mock(SharedAbuseLimiter.class);when(store.postgres()).thenReturn(true);when(store.allow(anyString(),anyString())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic outage"));
  var filter=new OnboardingRateLimit(Clock.systemUTC(),store);
  for(String path:java.util.List.of("register-owner","registration/owner","registration/invitation","verify-email","verification/request","forgot-password","reset-password")){
   var request=new MockHttpServletRequest("POST","/api/auth/"+path);var response=new MockHttpServletResponse();
   filter.doFilter(request,response,(r,s)->{throw new AssertionError("Must fail closed");});
   assertThat(response.getStatus()).isEqualTo(503);assertThat(response.getContentAsString()).doesNotContain("synthetic outage");
  }
 }
}
