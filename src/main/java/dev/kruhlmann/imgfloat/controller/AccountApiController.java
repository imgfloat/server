package dev.kruhlmann.imgfloat.controller;

import dev.kruhlmann.imgfloat.model.OauthSessionUser;
import dev.kruhlmann.imgfloat.service.AccountService;
import dev.kruhlmann.imgfloat.util.LogSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public class AccountApiController {

    private static final Logger LOG = LoggerFactory.getLogger(AccountApiController.class);

    private final AccountService accountService;

    public AccountApiController(AccountService accountService) {
        this.accountService = accountService;
    }

    @DeleteMapping
    public void deleteAccount(
        OAuth2AuthenticationToken oauthToken,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        String sessionUsername = OauthSessionUser.from(oauthToken).login();
        String logSessionUsername = LogSanitizer.sanitize(sessionUsername);
        LOG.info("Deleting account for {}", logSessionUsername);
        accountService.deleteAccount(sessionUsername);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            new SecurityContextLogoutHandler().logout(request, response, auth);
        }
    }
}
