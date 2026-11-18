package in.srmist.revaluation.service;

import in.srmist.revaluation.entity.Examiner;
import in.srmist.revaluation.entity.StaffAccount;
import in.srmist.revaluation.exception.ApiException;
import in.srmist.revaluation.repository.StaffAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Staff login: temporary passwords for examiners, BCrypt hashes, bearer tokens. */
class AuthServiceTest {

    private StaffAccountRepository repo;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        repo = mock(StaffAccountRepository.class);
        auth = new AuthService(repo);
        when(repo.findByUsername(any())).thenReturn(Optional.empty());
    }

    @Test
    void examinerCanSignInWithTheEmailedPasswordAndOnlyTheHashIsStored() {
        Examiner priya = new Examiner("EXM-102", "Dr. B. Priya", "b@srmist.test", "CSE", "21CSC305P", 0);
        ArgumentCaptor<StaffAccount> saved = ArgumentCaptor.forClass(StaffAccount.class);

        AuthService.Credentials login = auth.issueTemporaryPassword(priya);
        verify(repo).save(saved.capture());
        when(repo.findByUsername("exm-102")).thenReturn(Optional.of(saved.getValue()));

        assertThat(login.username()).isEqualTo("exm-102");
        assertThat(saved.getValue().getPasswordHash()).doesNotContain(login.password()).startsWith("$2");   // BCrypt
        String token = auth.login("EXM-102", login.password());   // username is not case sensitive
        assertThat(auth.require("Bearer " + token).role()).isEqualTo(StaffAccount.EXAMINER);
    }

    @Test
    void wrongPasswordAndMissingTokenAreRejected() {
        Examiner priya = new Examiner("EXM-102", "Dr. B. Priya", "b@srmist.test", "CSE", "21CSC305P", 0);
        ArgumentCaptor<StaffAccount> saved = ArgumentCaptor.forClass(StaffAccount.class);
        auth.issueTemporaryPassword(priya);
        verify(repo).save(saved.capture());
        when(repo.findByUsername("exm-102")).thenReturn(Optional.of(saved.getValue()));

        assertThatThrownBy(() -> auth.login("exm-102", "not-the-password"))
                .isInstanceOf(ApiException.class).hasMessage("Wrong username or password.");
        assertThatThrownBy(() -> auth.require(null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> auth.require("Bearer made-up-token")).isInstanceOf(ApiException.class);
    }
}
