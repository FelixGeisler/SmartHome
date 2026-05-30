package org.example.dashboard;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements ApplicationRunner {

    private final DashboardConfigRepository repo;

    public DataInitializer(DashboardConfigRepository repo) {
        this.repo = repo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repo.count() == 0) {
            DashboardConfig home = new DashboardConfig();
            home.setName("Home");
            home.setSortOrder(0);
            repo.save(home);
        }
    }
}
