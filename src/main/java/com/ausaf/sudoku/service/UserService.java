package com.ausaf.sudoku.service;

import com.ausaf.sudoku.entity.User;
import com.ausaf.sudoku.repository.user.UserRepository;
import com.ausaf.sudoku.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public boolean addUser(User user) {
        if (userRepository.findByName(user.getName()) != null) {
            return false;
        }
        String encryptedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(encryptedPassword);
        user.setId(null);
        userRepository.save(user);
        return true;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public boolean authenticateUser(String name, String password) {
        User user = userRepository.findByName(name);
        if (user == null) {
            return false;
        }
        return passwordEncoder.matches(password, user.getPassword());
    }

    public String generateToken(String name) {
        return jwtUtil.generateToken(name);
    }

}
