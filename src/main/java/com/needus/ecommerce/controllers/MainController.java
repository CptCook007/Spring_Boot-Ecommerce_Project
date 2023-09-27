package com.needus.ecommerce.controllers;

import com.needus.ecommerce.entity.user.ConfirmationToken;
import com.needus.ecommerce.entity.user.UserInformation;
import com.needus.ecommerce.repository.user.UserInformationRepository;
import com.needus.ecommerce.service.verification.ConfirmationTokenService;
import com.needus.ecommerce.service.user.UserInformationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.sql.Timestamp;

@Controller
@Slf4j
@EnableWebSecurity
@Configuration
public class MainController {
    @Autowired
    UserInformationService userInformationService;
    @Autowired
    ConfirmationTokenService tokenService;
    @Autowired
    UserInformationRepository repository;
//    @Bean
//    public AuthenticationProvider authenticationProvider(){
//        return new CustomAuthenticationManager();
//    }
//    private void authenticateUserAndSetSession(UserInformation user, HttpServletRequest request) {
//        String username = user.getUsername();
//        String password = user.getPassword();
//        UsernamePasswordAuthenticationToken token =
//            new UsernamePasswordAuthenticationToken(username, password);
//        token.setDetails(new WebAuthenticationDetails(request));
//        Authentication authenticatedUser = authenticationProvider().authenticate(token);
//        SecurityContextHolder.getContext().setAuthentication(authenticatedUser);
//        request.getSession().setAttribute("Sample", SecurityContextHolder.getContext().getAuthentication());
//    }
    @GetMapping("/")
    public String index(){
        return "redirect:/shop/home";
    }
    @GetMapping("/login")
    public String login(){
        return "login";
    }
    @GetMapping("/signup")
    public String signup(){
        return "register";
    }
    @PostMapping ("/register")
    public String register
        (@ModelAttribute UserInformation user, Model model, RedirectAttributes ra)
    {
        if(repository.existsByUsername(user.getUsername())){
            return "redirect:/signup?userNameError=true";
        }
        if(repository.existsByEmail(user.getEmail())){
            return "redirect:/signup?emailError=true";
        }
        ra.addFlashAttribute("message","Success! A verification email has been sent to you email");
        userInformationService.register(user);
        return "redirect:/login?registrationSuccess=true";
    }
    @GetMapping("/activation")
    public String activation(@RequestParam("token") String token,Model model){
        ConfirmationToken confirmationToken = tokenService.findByToken(token);
        if(confirmationToken == null){
            model.addAttribute("message","Your verification token is invalid");

        }
        else{
            UserInformation userInformation = confirmationToken.getUserInformation();
            if(!userInformation.isEnabled()){
                Timestamp currentTime = new Timestamp(System.currentTimeMillis());
                if(confirmationToken.getExpiryDate().before(currentTime)){
                    model.addAttribute("message","Your verification token has been expired");
                }
                else{
                    userInformation.setEnabled(true);
                    userInformationService.save(userInformation);
                    model.addAttribute("message","Your account is activated");
                }

            }
            else{
                model.addAttribute("message","Your account is already activated");
            }
        }
        return "activation";
    }
}
