package com.chatbot.event;


import com.chatbot.domain.ApiKey;
import com.chatbot.repository.UsersRepositoryImpl;
import io.quarkus.runtime.StartupEvent;

import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.transaction.Transactional;


@Singleton
public class Startup {
    @Transactional
    public void loadUsers(@Observes StartupEvent evt) {
        // reset and load all test users
        UsersRepositoryImpl.deleteAll();
        UsersRepositoryImpl.add("admin", "admin", "admin");
    }

    @Inject
    ApiKey apiKey;

    public void loadApikey(@Observes StartupEvent evt) {
        apiKey.init();
    }
}