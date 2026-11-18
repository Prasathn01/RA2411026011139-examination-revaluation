package in.srmist.revaluation.service;

import in.srmist.revaluation.entity.Examiner;
import in.srmist.revaluation.entity.StaffAccount;
import in.srmist.revaluation.exception.ApiException;
import in.srmist.revaluation.repository.StaffAccountRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Staff login for the portal (CoE and examiners).
 *
 * A successful login returns a random token; the portal sends it back in the header
 * "Authorization: Bearer <token>". Tokens are kept in memory, so restarting the backend
 * signs everybody out. This is enough for a local demo; a real deployment would use the
 * university's identity provider instead.
 */
@Service
public class AuthService {

    /** Who is calling. role is StaffAccount.COE or StaffAccount.EXAMINER. */
    public record StaffSession(String username, String displayName, String role, String examinerId) {
        public boolean isCoe() { return StaffAccount.COE.equals(role); }
    }

    /** Returned once by issueTemporaryPassword so the worker can email it; never stored in plain text. */
    public record Credentials(String username, String password) { }

    // No 0/O or 1/l/I, so the password is easy to type from the email
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final StaffAccountRepository accounts;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();
    private final Map<String, StaffSession> sessions = new ConcurrentHashMap<>();

    public AuthService(StaffAccountRepository accounts) {
        this.accounts = accounts;
    }

    /** Returns a new token, or 401 for an unknown user or a wrong password (same message for both). */
    public String login(String username, String password) {
        StaffAccount account = accounts.findByUsername(username.trim().toLowerCase(Locale.ROOT))
                .filter(a -> encoder.matches(password, a.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Wrong username or password."));
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, new StaffSession(account.getUsername(), account.getDisplayName(), account.getRole(), account.getExaminerId()));
        return token;
    }

    /** Resolves the "Authorization: Bearer ..." header; 401 when it is missing or the token is unknown. */
    public StaffSession require(String authorizationHeader) {
        String token = token(authorizationHeader);
        StaffSession session = token == null ? null : sessions.get(token);
        if (session == null) {
            throw ApiException.unauthorized("Please sign in to the staff portal.");
        }
        return session;
    }

    public void logout(String authorizationHeader) {
        String token = token(authorizationHeader);
        if (token != null) {
            sessions.remove(token);
        }
    }

    /**
     * Creates the examiner's account on the first assignment, or gives an existing account a new
     * temporary password. The newest emailed password is always the valid one.
     */
    @Transactional
    public Credentials issueTemporaryPassword(Examiner examiner) {
        String username = examiner.getExaminerId().toLowerCase(Locale.ROOT);
        StaffAccount account = accounts.findByUsername(username)
                .orElseGet(() -> new StaffAccount(username, examiner.getName(), StaffAccount.EXAMINER, examiner.getExaminerId()));
        String password = randomPassword();
        account.setPasswordHash(encoder.encode(password));
        accounts.save(account);
        return new Credentials(username, password);
    }

    /** Used once at startup: creates the CoE account if it does not exist yet. */
    @Transactional
    public void ensureCoeAccount(String password) {
        if (accounts.findByUsername("coe").isEmpty()) {
            StaffAccount coe = new StaffAccount("coe", "Controller of Examinations", StaffAccount.COE, null);
            coe.setPasswordHash(encoder.encode(password));
            accounts.save(coe);
        }
    }

    private static String token(String authorizationHeader) {
        return authorizationHeader != null && authorizationHeader.startsWith("Bearer ")
                ? authorizationHeader.substring(7).trim() : null;
    }

    private String randomPassword() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
