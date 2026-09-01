package com.ausaf.sudoku.controllers;

import com.ausaf.sudoku.entity.User;
import com.ausaf.sudoku.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("users")
public class UsersController {

    @Autowired
    private UserService userService;

    @GetMapping
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @PostMapping("addUser")
    public Boolean addUser(@RequestBody User user) {
        return userService.addUser(user);
    }

    @PostMapping("signIn")
    public String signIn(@RequestBody User user) {
        if (userService.authenticateUser(user.getName(), user.getPassword())) {
            return userService.generateToken(user.getName());
        }
        return null;
    }
}
