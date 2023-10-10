package com.concordeu.profile.validation;

import com.concordeu.client.common.dto.UserRequestDto;
import com.concordeu.profile.excaption.InvalidRequestDataException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class ValidateDataImpl implements ValidateData {

    @Override
    public boolean validateRequest(UserRequestDto requestDto) {
        return isValidUsername(requestDto.username()) &&
               isValidPassword(requestDto.password()) &&
               isValidFirstName(requestDto.firstName()) &&
               isValidLastName(requestDto.lastName()) &&
               isValidCity(requestDto.city()) &&
               isValidStreet(requestDto.street()) &&
               isValidPostCode(requestDto.postCode()) &&
               isValidPhoneNumber(requestDto.phoneNumber());

    }

    @Override
    public boolean isValidUsername(String username) {
        //language=RegExp
        String regexPatternEmail = "^[\\w-.]+@([\\w-]+\\.)+[\\w-]{2,4}$";

        if (isLengthNotValid(username, 5, 50) ||
            isNotMatches(username, regexPatternEmail)) {
            log.warn("Username is not correct: {}", username);
            throw new InvalidRequestDataException(
                    String.format("Username is not correct: %s. For example: example@gmail.com", username));
        }
        return true;
    }

    @Override
    public boolean isValidPassword(String password) {
        //- a digit must occur at least once
        //- a lower case letter must occur at least once
        //- an upper case letter must occur at least once
        //- a special character must occur at least once
        //- no whitespace allowed in the entire string
        //language=RegExp
        String regexPatternPassword = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\\S+$).{6,30}$";
        if (password.equals("opaque")){
            return true;
        }
        if (isLengthNotValid(password, 6, 30) ||
            isNotMatches(password, regexPatternPassword)) {
            log.warn("Password is not correct");
            throw new InvalidRequestDataException(
                    "Password is not correct");
        }
        return true;
    }

    private boolean isValidPhoneNumber(String phoneNumber) {
        //- the number has ten digits and nothing else: 2055550125
        //- allow optional whitespace, dots, or hyphens (-) between the numbers: 2055550125, 202 555 0125, 202.555.0125, and 202-555-0125
        //- the first part of our phone between parentheses: (202)5550125, (202) 555-0125 or (202)-555-0125
        //-  allow an international prefix at the start of a phone number: +111 (202) 555-0125
        //language=RegExp
        String regexPatternPhoneNumber = "^\\d{10}$" +
                                         "|^(\\d{3}[- .]?){2}\\d{4}$" +
                                         "|^((\\(\\d{3}\\))|\\d{3})[- .]?\\d{3}[- .]?\\d{4}$" +
                                         "|^(\\+\\d{1,3}( )?)?((\\(\\d{3}\\))|\\d{3})[- .]?\\d{3}[- .]?\\d{4}$";

        if (isLengthNotValid(phoneNumber, 9, 19) ||
            isNotMatches(phoneNumber, regexPatternPhoneNumber)) {
            log.warn("Phone number is not correct: {}", phoneNumber);
            throw new InvalidRequestDataException(
                    String.format("Phone number is not correct: %s. For example: 20555501252, 202 555 0125, 202.555.0125, 202-555-0125, (202)5550125, (202) 555-0125 or (202)-555-0125, +111 (202) 555-0125", phoneNumber));
        }
        return true;
    }

    private boolean isValidPostCode(String postCode) {
        if (postCode.isBlank()) {
            log.warn("Post code is not correct: {}", postCode);
            throw new InvalidRequestDataException(
                    String.format("Post code is not correct: %s. For example: 9001", postCode));
        }
        return true;
    }

    private boolean isValidStreet(String street) {
        if (street.isBlank()) {
            log.warn("Street is not correct: {}", street);
            throw new InvalidRequestDataException(
                    String.format("Street is not correct: %s. For example: Katya Paskaleva", street));
        }
        return true;
    }

    private boolean isValidCity(String city) {
        if (city.isBlank()) {
            log.warn("City is not correct: {}", city);
            throw new InvalidRequestDataException(
                    String.format("City is not correct: %s. For example: Varna", city));
        }
        return true;
    }

    private boolean isValidLastName(String lastName) {
        //Last name must contain uppercase first letter and then lowercase letters
        String regexPatternFirstName = "^([A-Z])(\\p{L})(?=\\S+$).{1,12}$";

        //Last name cannot contain digit/digits
        String regexPatternFirstNameWithoutNumber = "\\D*";

        if (isLengthNotValid(lastName, 3, 12) ||
            isNotMatches(lastName, regexPatternFirstName) ||
            isNotMatches(lastName, regexPatternFirstNameWithoutNumber)) {
            log.warn("Last name is not correct: {}", lastName);
            throw new InvalidRequestDataException(
                    String.format("Last name is not correct: %s. For example: Ivanov", lastName));
        }
        return true;
    }

    private boolean isValidFirstName(String firstName) {
        //First name must contain uppercase first letter and then lowercase letters
        //language=RegExp
        String regexPatternFirstName = "^([A-Z])(\\p{L})(?=\\S+$).{1,12}$";

        //First name cannot contain digit/digits
        String regexPatternFirstNameWithoutNumber = "\\D*";

        if (isLengthNotValid(firstName, 3, 12) ||
            isNotMatches(firstName, regexPatternFirstName) ||
            isNotMatches(firstName, regexPatternFirstNameWithoutNumber)) {
            log.warn("First name is not correct: {}", firstName);
            throw new InvalidRequestDataException(
                    String.format("First name is not correct: %s. For example: Ivan", firstName));
        }
        return true;
    }

    private boolean isLengthNotValid(String content, int minLength, int maxLength) {
        return content.length() < minLength || content.length() > maxLength;
    }

    private boolean isNotMatches(String content, String patternString) {
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(content);
        return !matcher.matches();
    }

}
