package com.example.payflow.repository;

import com.example.payflow.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByUpiId(String upiId);
}