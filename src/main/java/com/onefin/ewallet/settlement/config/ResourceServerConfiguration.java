package com.onefin.ewallet.settlement.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.FixedPrincipalExtractor;
import org.springframework.boot.autoconfigure.security.oauth2.resource.PrincipalExtractor;
import org.springframework.boot.autoconfigure.security.oauth2.resource.ResourceServerProperties;
import org.springframework.boot.autoconfigure.security.oauth2.resource.UserInfoTokenServices;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.token.grant.code.AuthorizationCodeResourceDetails;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer;

import java.util.Map;

@Configuration
@EnableResourceServer
public class ResourceServerConfiguration extends ResourceServerConfigurerAdapter {

	@Autowired
	private ClientResources clientResources;

	@Override
	public void configure(HttpSecurity http) throws Exception {
//		http.anonymous().disable().requestMatchers().antMatchers("/me/**").and().authorizeRequests()
//				.antMatchers("/me/**").access("hasRole('ADMIN') or hasRole('USER')").and().exceptionHandling()
//				.accessDeniedHandler(new OAuth2AccessDeniedHandler());
//		http.antMatcher("/**").authorizeRequests().anyRequest().authenticated();
		http.authorizeRequests()
				.antMatchers("/inside/**").hasRole("ADMIN")
				.anyRequest().permitAll();
	}

	@Override
	public void configure(ResourceServerSecurityConfigurer config) {
		config.tokenServices(tokenServices());
	}

	@Bean
	@Primary
	public UserInfoTokenServices tokenServices() {
		ClientResources client = clientResources;
		UserInfoTokenServices tokenServices = new UserInfoTokenServices(client.getResource().getUserInfoUri(), client.getClient().getClientId()) {
			private PrincipalExtractor principalExtractor = new FixedPrincipalExtractor();

			protected Object getPrincipal(Map<String, Object> map) {
				String principal = (String) this.principalExtractor.extractPrincipal(map);
				return (principal == null ? "unknown" : principal.toLowerCase());
			}
		};
		return tokenServices;
	}

	@Bean
	@ConfigurationProperties("security.oauth2")
	public ClientResources oauth2() {
		return new ClientResources();
	}

}

class ClientResources {

	@NestedConfigurationProperty
	private AuthorizationCodeResourceDetails client = new AuthorizationCodeResourceDetails();

	@NestedConfigurationProperty
	private ResourceServerProperties resource = new ResourceServerProperties();

	public AuthorizationCodeResourceDetails getClient() {
		return client;
	}

	public ResourceServerProperties getResource() {
		return resource;
	}
}
