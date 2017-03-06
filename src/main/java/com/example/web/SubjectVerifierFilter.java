package com.example.web;

import java.io.IOException;
import java.security.AccessController;
import java.security.Principal;
import java.util.Set;
import java.util.function.Predicate;

import javax.security.auth.Subject;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.catalina.realm.GenericPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.web.filter.GenericFilterBean;

public class SubjectVerifierFilter extends GenericFilterBean {

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		Subject subject = Subject.getSubject(AccessController.getContext());
		if (subject == null) {
			throw new IllegalStateException("no subject - check if security is enabled. Tomcat 7 needs JVM args '-Djava.security.manager -Djava.security.policy=.../src/test/tomcat7/grantAll.policy'");
		}
		
		Set<Principal> principals = subject.getPrincipals();
		if (principals.isEmpty()) {
			throw new IllegalStateException("empty principal set");
		}
		boolean springPrincipalFound =
			principals
			.stream()
			.anyMatch(isSpringPrincipal);

		if (springPrincipalFound && principals.size() == 1) {
			// this is an error, there must always be a Tomcat-managed principal 
			throw new IllegalStateException("Only Spring principal found in subject.");
		}

		chain.doFilter(request, response);
	}

	// is this our custom principal?
	private final Predicate<Principal> isSpringPrincipal = (principal) -> {
		if (principal instanceof GenericPrincipal) {
			// actual principal got a wrapped into a "GenericPrincipal"
			return ((GenericPrincipal) principal).getUserPrincipal() instanceof Authentication;
		}
		return principal instanceof Authentication;
	};

}
