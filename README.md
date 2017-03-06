# Broken subject caching in Tomcat?

This web application reproduces an issue with the following setup:

- custom login module
- Spring Security with pre-authenticated scenario

It is necessary to _activate global security_. The embedded Tomcat is run inside the Maven JVM, thus the following `MAVEN_OPTS` must be set:

	MAVEN_OPTS=-Djava.security.manager -Djava.security.policy=<path-to-workspace>/src/test/tomcat7/grantAll.policy

To simply access the application run

	mvn tomcat7:run

The application is available at http://localhost:8080/demo/hello and you can log in with `user123` and `pass123` as the password.

To reproduce the issue run the integration test by executing

	mvn verify

The test should fail, indicating the problem (a number of requests fail with status code 500 because an expected `Principal` cannot be found). Check out [ConcurrentRequestsIT.java](src/test/java/com/example/ConcurrentRequestsIT.java)

## What is the issue?

Briefly, Tomcat tries to cache the `Subject` in the user session, but wipes it from the session at the beginning of every request. During requests, the `Subject` may be placed into the session, and the subject's set of `Principal` objects may have unexpected content.

More details can be found at https://bz.apache.org/bugzilla/show_bug.cgi?id=60824
