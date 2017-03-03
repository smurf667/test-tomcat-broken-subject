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
import org.springframework.web.filter.GenericFilterBean;

import com.example.jaas.UserPrincipal;

public class SubjectVerifierFilter extends GenericFilterBean {

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		Subject subject = Subject.getSubject(AccessController.getContext());
		if (subject == null) {
			throw new IllegalStateException("no subject - check if security is enabled. Tomcat 7 needs JVM args '-Djava.security.manager -Djava.security.policy=.../src/test/tomcat7/grantAll.policy'");
		}
		
		Set<Principal> principals = subject.getPrincipals();
		boolean myPrincipalFound =
			principals
			.stream()
			.anyMatch(isCustomPrincipal);

		if (!myPrincipalFound) {
			throw new IllegalStateException("Custom user principal not found in subject. Principals seen: " + principals);
		}
		
		chain.doFilter(request, response);
	}

	// is this our custom principal?
	private final Predicate<Principal> isCustomPrincipal = (principal) -> {
		if (principal instanceof GenericPrincipal) {
			// sometimes we're getting wrapped into a "GenericPrincipal"
			return ((GenericPrincipal) principal).getUserPrincipal() instanceof UserPrincipal;
		}
		return principal instanceof UserPrincipal;
	};

}
