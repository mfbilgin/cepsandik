package com.cepsandik.userservice.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogAudit {
    String action(); // Örneğin: "LOGIN", "REGISTER"
    String details() default ""; // Sabit bir detay yazmak istersen
}