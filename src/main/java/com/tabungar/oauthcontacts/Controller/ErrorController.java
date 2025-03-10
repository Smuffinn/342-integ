package com.tabungar.oauthcontacts.Controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class ErrorController implements org.springframework.boot.web.servlet.error.ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        String errorMessage = "An unexpected error occurred";
        String errorDetails = "";
        
        if (status != null) {
            Integer statusCode = Integer.valueOf(status.toString());
            
            if(statusCode == HttpStatus.NOT_FOUND.value()) {
                errorMessage = "Page not found";
                errorDetails = "The page you are looking for might have been removed or is temporarily unavailable";
            }
            else if(statusCode == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
                errorMessage = "Internal server error";
                errorDetails = "Our servers are having issues right now";
            }
            else if(statusCode == HttpStatus.FORBIDDEN.value()) {
                errorMessage = "Access denied";
                errorDetails = "You don't have permission to access this resource";
            }
            
            model.addAttribute("statusCode", statusCode);
        }
        
        model.addAttribute("errorMessage", errorMessage);
        model.addAttribute("errorDetails", errorDetails);
        
        return "error";
    }
}
