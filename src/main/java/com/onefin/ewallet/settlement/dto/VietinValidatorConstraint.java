package com.onefin.ewallet.settlement.dto;

import com.onefin.ewallet.common.base.constants.OneFinConstants;
import com.onefin.ewallet.common.base.errorhandler.RuntimeConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Pattern;

public class VietinValidatorConstraint implements ConstraintValidator<VietinValidator, Date> {
	private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
	private static final Logger LOGGER = LoggerFactory.getLogger(VietinValidatorConstraint.class);
	private static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(OneFinConstants.DATE_FORMAT_yyyyMMdd);

	public void initialize(VietinValidator constraintAnnotation) {

	}

	public boolean isValid(Date value, ConstraintValidatorContext cxt) {
		try {
			if (value == null) {
				return false;
			}

			String dateString = DATE_FORMAT.format(value);

			return DATE_PATTERN.matcher(dateString).matches();

		} catch (Exception e) {
			throw new RuntimeConflictException("Invalid date format");
		}
	}
}
