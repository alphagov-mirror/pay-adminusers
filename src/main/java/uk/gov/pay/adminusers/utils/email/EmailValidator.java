package uk.gov.pay.adminusers.utils.email;

import com.google.common.base.Joiner;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmailValidator {

    /**
     * {@link #PUBLIC_SECTOR_EMAIL_DOMAIN_REGEX_PATTERNS} is based on:<br>
     * - <a href="https://en.wikipedia.org/wiki/.uk">en.wikipedia.org/wiki/.uk</a><br>
     * - <a href="https://github.com/alphagov/notifications-admin/blob/9391181b2c7d077ea8fe0a72c718ab8f7fdbcd0c/app/config.py#L67">alphagov/notifications-admin</a><br>
     */
    private static final List<String> PUBLIC_SECTOR_EMAIL_DOMAIN_REGEX_PATTERNS;
    static {
        PUBLIC_SECTOR_EMAIL_DOMAIN_REGEX_PATTERNS = Collections.unmodifiableList(Arrays.asList(
                "assembly\\.wales",
                "cynulliad\\.cymru",
                "gov\\.scot",
                "gov\\.uk",
                "gov\\.wales",
                "hmcts\\.net",
                "judiciary\\.uk",
                "llyw\\.cymru",
                "mil\\.uk",
                "mod\\.uk",
                "naturalengland\\.org\\.uk",
                "nhs\\.net",
                "nhs\\.uk",
                "parliament\\.scot",
                "parliament\\.uk",
                "police\\.uk",
                "scotent\\.co\\.uk",
                "slc\\.co\\.uk",
                "ucds\\.email"
        ));
    }
    private static final Pattern PUBLIC_SECTOR_EMAIL_DOMAIN_REGEX_PATTERN;
    static {
        String domainRegExPatternString = Joiner.on("|").join(PUBLIC_SECTOR_EMAIL_DOMAIN_REGEX_PATTERNS);

        // We are splitting the logic into two parts for whitelisted domains and subdomains
        String regExDomainsOnlyPart = "(" + domainRegExPatternString + ")";
        String regExSubdomainsPart = "(((?!-)[A-Za-z0-9-]+(?<!-)\\.)+(" + domainRegExPatternString + "))";
        PUBLIC_SECTOR_EMAIL_DOMAIN_REGEX_PATTERN =
                Pattern.compile("^" + regExDomainsOnlyPart +  "|" + regExSubdomainsPart + "$");
    }

    /**
     * This method checks that an email belongs to a public sector domain name
     * <p>
     * The logic is split into two parts:
     * <ol>
     *   <li>checks the subdomains and that they:</li>
     *   <ul>
     *     <li>don't start with "-" </li>
     *     <li>only have any alphanumeric characters or "-"</li>
     *     <li>don't end with "-"</li>
     *   </ul>
     *   <li>checks the whitelisted domains</li>
     * </ol>
     * </p>
     *
     * @param email the email to check
     * @return <b>boolean</b> whether or not it is from a public sector domain
     */
    public static boolean isPublicSectorEmail(String email) {
        email = email.toLowerCase();
        String[] emailParts = email.split("@");
        if ((emailParts.length != 2) || emailParts[0].isEmpty() || emailParts[1].isEmpty()) {
            return false;
        }
        String domain = emailParts[1];

        Matcher matcher = PUBLIC_SECTOR_EMAIL_DOMAIN_REGEX_PATTERN.matcher(domain);

        return matcher.matches();
    }
}