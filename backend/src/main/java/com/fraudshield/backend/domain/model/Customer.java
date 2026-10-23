package com.fraudshield.backend.domain.model;

import com.fraudshield.backend.auth.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "customers")
public class Customer {
    @Id @Column(name = "customer_id", length = 40) private String id;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id") private UserAccount user;
    @Column(name = "full_name", nullable = false, length = 200) private String fullName;
    @Column(nullable = false, length = 40) private String mobile;
    @Column(nullable = false, length = 254) private String email;
    @Column(nullable = false, length = 100) private String city;
    @Column(nullable = false, unique = true, length = 100) private String username;
    protected Customer() {}
    public String getId() { return id; }
    public UserAccount getUser() { return user; }
}
