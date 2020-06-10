package nl.trifork.springsessionjdbcsqlserverbugs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import java.util.Date;

@SpringBootApplication
public class SpringSessionJdbcSqlServerBugsApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringSessionJdbcSqlServerBugsApplication.class, args);
	}

	@RestController
	static class SomeController {
		@GetMapping("/")
		String updateSession(HttpSession session, @RequestParam(defaultValue = "false") boolean secondAttr) {
			session.setAttribute("date", new Date());
			if (secondAttr) {
				session.setAttribute("second", "whatever");
			}
			return session.getId();
		}

	}
}
