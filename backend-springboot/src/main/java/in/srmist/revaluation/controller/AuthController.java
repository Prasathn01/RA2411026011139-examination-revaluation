package in.srmist.revaluation.controller;

import in.srmist.revaluation.dto.TaskRequests;
import in.srmist.revaluation.dto.Views.LoginResponse;
import in.srmist.revaluation.dto.Views.Message;
import in.srmist.revaluation.service.AuthService;
import in.srmist.revaluation.service.AuthService.StaffSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

/** Staff portal sign-in (CoE and examiners). Students do not sign in. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody TaskRequests.Login body) {
        String token = auth.login(body.username(), body.password());
        return response(token, auth.require("Bearer " + token));
    }

    /** Who is signed in (the portal calls this on page load to check the saved token is still valid). */
    @GetMapping("/me")
    public LoginResponse me(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return response(null, auth.require(authorization));
    }

    @PostMapping("/logout")
    public Message logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        auth.logout(authorization);
        return new Message("Signed out");
    }

    private static LoginResponse response(String token, StaffSession s) {
        return new LoginResponse(token, s.username(), s.displayName(), s.role());
    }
}
