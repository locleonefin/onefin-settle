package com.onefin.ewallet.settlement.dto;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.*;

@Documented
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = VietinValidatorConstraint.class)
public @interface VietinValidator {
	String message() default "Invalid date format";

	Class<? extends Payload>[] payload() default {};

	Class<?>[] groups() default {};

}
