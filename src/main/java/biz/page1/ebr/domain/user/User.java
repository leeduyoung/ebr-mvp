package biz.page1.ebr.domain.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_qualifications",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "qualification_id")
    )
    private Set<Qualification> qualifications = new HashSet<>();

    private boolean enabled = true;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public User(String username, String password, String name, UserRole role) {
        this.username = username;
        this.password = password;
        this.name = name;
        this.role = role;
    }

    public void addQualification(Qualification qualification) {
        this.qualifications.add(qualification);
    }

    public boolean hasQualification(Qualification qualification) {
        return this.qualifications.contains(qualification);
    }

    public boolean hasAllQualifications(Set<Qualification> requiredQualifications) {
        return this.qualifications.containsAll(requiredQualifications);
    }
}
