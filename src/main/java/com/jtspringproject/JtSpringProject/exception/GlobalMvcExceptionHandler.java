package com.jtspringproject.JtSpringProject.exception;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;

import com.jtspringproject.JtSpringProject.controller.AdminController;
import com.jtspringproject.JtSpringProject.controller.CartController;
import com.jtspringproject.JtSpringProject.controller.UserController;

/**
 * Renders errors from the server-rendered controllers as HTML rather than
 * letting them reach the container's default stack-trace page.
 */
@ControllerAdvice(assignableTypes = { UserController.class, AdminController.class, CartController.class })
public class GlobalMvcExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalMvcExceptionHandler.class);

	@ExceptionHandler(ResourceNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ModelAndView handleNotFound(ResourceNotFoundException e) {
		return errorView("Not found", e.getMessage());
	}

	@ExceptionHandler(BusinessRuleException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ModelAndView handleBusinessRule(BusinessRuleException e) {
		return errorView("That did not work", e.getMessage());
	}

	@ExceptionHandler(Exception.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public ModelAndView handleUnexpected(Exception e) {
		String errorId = UUID.randomUUID().toString();
		log.error("Unhandled exception in MVC controller [errorId={}]", errorId, e);
		return errorView("Something went wrong",
				"An unexpected error occurred. Reference: " + errorId);
	}

	private ModelAndView errorView(String heading, String detail) {
		ModelAndView mv = new ModelAndView("error");
		mv.addObject("heading", heading);
		mv.addObject("detail", detail);
		return mv;
	}
}
