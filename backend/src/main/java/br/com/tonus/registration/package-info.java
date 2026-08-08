/**
 * Registration of students and teachers.
 *
 * Depends only on shared. It is the most consumed module in the system — scheduling,
 * billing and notification point here — so keeping it free of business
 * dependency is what avoids a cycle further down the line.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = "shared")
package br.com.tonus.registration;
