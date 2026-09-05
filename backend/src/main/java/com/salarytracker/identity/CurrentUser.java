package com.salarytracker.identity;

import java.util.Set;

public record CurrentUser(long id, String username, String nickname, Set<String> authorities) {
}
