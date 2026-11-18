package in.srmist.revaluation.entity;

import jakarta.persistence.*;

/**
 * A login for the staff portal. Two roles:
 *  - COE      the Controller of Examinations (one account, created on first start by CoeAccountSeeder);
 *  - EXAMINER created the first time the CoE assigns that examiner, with a temporary password
 *             that is emailed to the examiner and stored here only as a BCrypt hash.
 * Students do not log in: they apply and follow their application by its link.
 */
@Entity
@Table(name = "staff_account")
public class StaffAccount {

    public static final String COE = "COE";
    public static final String EXAMINER = "EXAMINER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Lower case, e.g. "coe" or "exm-102" (the examiner ID in lower case). */
    @Column(nullable = false, unique = true, length = 40)
    private String username;

    @Column(nullable = false, length = 80)
    private String displayName;

    @Column(nullable = false, length = 10)
    private String role;

    /** BCrypt hash; the plain password is never stored. */
    @Column(nullable = false, length = 100)
    private String passwordHash;

    /** Link to the examiner directory (null for the CoE). */
    @Column(length = 20)
    private String examinerId;

    protected StaffAccount() { }

    public StaffAccount(String username, String displayName, String role, String examinerId) {
        this.username = username;
        this.displayName = displayName;
        this.role = role;
        this.examinerId = examinerId;
    }

    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getRole() { return role; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getExaminerId() { return examinerId; }
}
