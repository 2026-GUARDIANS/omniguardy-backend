package com.omniguardy.backend.domain.user.application.usecase;

import com.omniguardy.backend.domain.user.domain.model.User;
import com.omniguardy.backend.domain.user.domain.repository.UserRepository;
import com.omniguardy.backend.domain.user.domain.error.UserErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DemoUserResolverTest {
    private final UserRepository repository = mock(UserRepository.class);
    private final MockEnvironment environment = new MockEnvironment();
    private final DemoUserResolver resolver = new DemoUserResolver(repository, environment);

    @Test void actualDevConfigurationSelectsUserOne() throws Exception {
        var loader = new YamlPropertySourceLoader();
        var defaults = loader.load("defaults", new ClassPathResource("application.yml")).getFirst();
        assertEquals("dev", defaults.getProperty("spring.profiles.active"));
        var prod = loader.load("prod", new ClassPathResource("application-prod.yml")).getFirst();
        assertEquals("${OMNIGUARDY_DEMO_USERID}", prod.getProperty("omniguardy.demo.user-id"));
        environment.setActiveProfiles("dev");
        loader.load("dev", new ClassPathResource("application-dev.yml"))
                .forEach(source -> environment.getPropertySources().addFirst(source));
        User user = mock(User.class);
        when(repository.findById(1L)).thenReturn(Optional.of(user));
        assertSame(user, resolver.resolve().orElseThrow());
        for (String profile : new String[]{"test"}) {
            var sources = loader.load(profile, new ClassPathResource("application-" + profile + ".yml"));
            assertTrue(sources.stream().allMatch(source -> source.getProperty("omniguardy.demo.user-id") == null));
        }
    }

    @Test void devUsesConfiguredIdWithoutHardcoding() {
        environment.setActiveProfiles("dev");
        environment.setProperty("omniguardy.demo.user-id", "42");
        User user = mock(User.class);
        when(repository.findById(42L)).thenReturn(Optional.of(user));
        assertSame(user, resolver.resolve().orElseThrow());
    }

    @Test void missingConfigurationDoesNotSelectUser() {
        for (String[] profiles : new String[][]{{}, {"dev"}, {"test"}, {"prod"}, {"dev", "prod"}}) {
            environment.setActiveProfiles(profiles);
            assertEquals(UserErrorCode.DEMO_USER_ID_NOT_CONFIGURED,
                    assertThrows(BusinessException.class, resolver::resolve).getErrorCode());
        }
        verifyNoInteractions(repository);
    }

    @Test void allProfilesUseExplicitConfiguration() {
        environment.setProperty("omniguardy.demo.user-id", "1");
        User user = mock(User.class);
        when(repository.findById(1L)).thenReturn(Optional.of(user));
        for (String[] profiles : new String[][]{{}, {"dev"}, {"test"}, {"prod"}, {"dev", "prod"}}) {
            environment.setActiveProfiles(profiles);
            assertSame(user, resolver.resolve().orElseThrow());
        }
        verify(repository, times(5)).findById(1L);
    }

    @Test void missingUserThrowsExistingBusinessError() {
        environment.setProperty("omniguardy.demo.user-id", "1");
        for (String profile : new String[]{"dev", "prod"}) {
            environment.setActiveProfiles(profile);
            assertEquals(UserErrorCode.USER_NOT_FOUND,
                    assertThrows(BusinessException.class, resolver::resolve).getErrorCode());
        }
    }
}
