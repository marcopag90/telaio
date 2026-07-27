package com.paganbit.telaio.showcase;

import com.paganbit.telaio.showcase.dal.announcement.Announcement;
import com.paganbit.telaio.showcase.dal.announcement.AnnouncementRepository;
import com.paganbit.telaio.showcase.dal.announcement.AnnouncementType;
import com.paganbit.telaio.showcase.dal.article.Article;
import com.paganbit.telaio.showcase.dal.article.ArticleRepository;
import com.paganbit.telaio.showcase.dal.article.ArticleStatus;
import com.paganbit.telaio.showcase.dal.bulletin.Bulletin;
import com.paganbit.telaio.showcase.dal.bulletin.BulletinRepository;
import com.paganbit.telaio.showcase.dal.department.Department;
import com.paganbit.telaio.showcase.dal.department.DepartmentRepository;
import com.paganbit.telaio.showcase.dal.employee.Employee;
import com.paganbit.telaio.showcase.dal.employee.EmployeeRepository;
import com.paganbit.telaio.showcase.dal.product.Product;
import com.paganbit.telaio.showcase.dal.product.ProductRepository;
import com.paganbit.telaio.showcase.dal.setting.AppSetting;
import com.paganbit.telaio.showcase.dal.setting.AppSettingRepository;
import com.paganbit.telaio.showcase.dal.ticket.SupportTicket;
import com.paganbit.telaio.showcase.dal.ticket.SupportTicketRepository;
import com.paganbit.telaio.showcase.dal.translation.Translation;
import com.paganbit.telaio.showcase.dal.translation.TranslationId;
import com.paganbit.telaio.showcase.dal.translation.TranslationRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class DataInitializer implements CommandLineRunner {

    private final ArticleRepository articleRepository;
    private final ProductRepository productRepository;
    private final AnnouncementRepository announcementRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final TranslationRepository translationRepository;
    private final AppSettingRepository appSettingRepository;
    private final BulletinRepository bulletinRepository;
    private final SupportTicketRepository supportTicketRepository;

    public DataInitializer(
        ArticleRepository articleRepository,
        ProductRepository productRepository,
        AnnouncementRepository announcementRepository,
        EmployeeRepository employeeRepository,
        DepartmentRepository departmentRepository,
        TranslationRepository translationRepository,
        AppSettingRepository appSettingRepository,
        BulletinRepository bulletinRepository,
        SupportTicketRepository supportTicketRepository
    ) {
        this.articleRepository = articleRepository;
        this.productRepository = productRepository;
        this.announcementRepository = announcementRepository;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.translationRepository = translationRepository;
        this.appSettingRepository = appSettingRepository;
        this.bulletinRepository = bulletinRepository;
        this.supportTicketRepository = supportTicketRepository;
    }

    @Override
    public void run(String... args) {
        seedArticles();
        seedProducts();
        seedAnnouncements();
        seedDepartments();
        seedEmployees();
        seedTranslations();
        seedAppSettings();
        seedBulletins();
        seedTickets();
    }

    private void seedTickets() {
        if (supportTicketRepository.count() > 0) {
            return;
        }
        SupportTicket ticket = new SupportTicket();
        ticket.setSubject("Onboarding: request VPN access");
        ticket.setStatus("OPEN");
        supportTicketRepository.save(ticket);
    }

    private void seedBulletins() {
        if (bulletinRepository.count() > 0) {
            return;
        }
        Bulletin welcome = new Bulletin();
        welcome.setTitle("Welcome to the Telaio showcase");
        welcome.setMessage("Everyone can read bulletins; only ADMIN can post, edit or delete them.");
        welcome.setPostedAt(LocalDateTime.now(ZoneId.systemDefault()).minusDays(2));
        bulletinRepository.save(welcome);
    }

    private void seedAppSettings() {
        if (appSettingRepository.count() > 0) {
            return;
        }
        appSettingRepository.save(setting("feature.beta-search.enabled", "false"));
        appSettingRepository.save(setting("catalog.page-size.default", "20"));
    }

    private AppSetting setting(String key, String value) {
        AppSetting setting = new AppSetting();
        setting.setId(key);
        setting.setValue(value);
        return setting;
    }

    private void seedDepartments() {
        if (departmentRepository.count() > 0) {
            return;
        }
        Department engineering = new Department();
        engineering.setName("Engineering");
        departmentRepository.save(engineering);

        Department design = new Department();
        design.setName("Design");
        departmentRepository.save(design);
    }

    private void seedArticles() {
        if (articleRepository.count() > 0) {
            return;
        }

        Article draft = new Article();
        draft.setTitle("Upcoming Features in Telaio 2.0");
        draft.setSlug("upcoming-features-telaio-2");
        draft.setContent("We are working on exciting new features including GraphQL support and reactive streams...");
        draft.setCategory("roadmap");
        draft.setStatus(ArticleStatus.DRAFT);
        draft.setAuthorEmail("developer@example.com");
        draft.setRevisionCount(5);
        articleRepository.save(draft);

        Article published1 = new Article();
        published1.setTitle("Getting Started with Telaio");
        published1.setSlug("getting-started-telaio");
        published1.setContent("Telaio is a Spring Boot framework that provides a unified Data Access Layer abstraction...");
        published1.setCategory("tutorial");
        published1.setStatus(ArticleStatus.PUBLISHED);
        published1.setPublishedAt(LocalDateTime.now(ZoneId.systemDefault()).minusDays(10));
        published1.setAuthorEmail("admin@example.com");
        published1.setRevisionCount(3);
        articleRepository.save(published1);

        Article published2 = new Article();
        published2.setTitle("Security in Telaio: Roles and RBAC");
        published2.setSlug("security-telaio-roles-rbac");
        published2.setContent("Telaio provides a layered security model with operation-level authorization and field-level RBAC...");
        published2.setCategory("security");
        published2.setStatus(ArticleStatus.PUBLISHED);
        published2.setPublishedAt(LocalDateTime.now(ZoneId.systemDefault()).minusDays(5));
        published2.setAuthorEmail("admin@example.com");
        published2.setRevisionCount(2);
        articleRepository.save(published2);

        Article archived = new Article();
        archived.setTitle("Telaio 1.0 Release Notes");
        archived.setSlug("telaio-1-0-release-notes");
        archived.setContent("Telaio 1.0 introduced the core DAL abstraction with JPA support...");
        archived.setCategory("release");
        archived.setStatus(ArticleStatus.ARCHIVED);
        archived.setPublishedAt(LocalDateTime.now(ZoneId.systemDefault()).minusMonths(6));
        archived.setAuthorEmail("developer@example.com");
        archived.setRevisionCount(1);
        articleRepository.save(archived);
    }

    private void seedProducts() {
        if (productRepository.count() > 0) {
            return;
        }

        Product laptop = new Product();
        laptop.setName("Developer Laptop Pro");
        laptop.setDescription("High-performance laptop for software development with 32GB RAM and dedicated GPU.");
        laptop.setPrice(new BigDecimal("1499.99"));
        laptop.setCostPrice(new BigDecimal("950.00"));
        laptop.setMarginPercentage(new BigDecimal("36.69"));
        laptop.setSku("LAP-DEV-001");
        laptop.setInternalSku("INT-LAP-2024-001");
        laptop.setCategory("electronics");
        laptop.setAvailable(true);
        productRepository.save(laptop);

        Product keyboard = new Product();
        keyboard.setName("Mechanical Keyboard RGB");
        keyboard.setDescription("Compact 75% mechanical keyboard with customizable RGB lighting and hot-swap switches.");
        keyboard.setPrice(new BigDecimal("129.99"));
        keyboard.setCostPrice(new BigDecimal("55.00"));
        keyboard.setMarginPercentage(new BigDecimal("57.69"));
        keyboard.setSku("KEY-MECH-001");
        keyboard.setInternalSku("INT-KEY-2024-001");
        keyboard.setCategory("peripherals");
        keyboard.setAvailable(true);
        productRepository.save(keyboard);

        Product monitor = new Product();
        monitor.setName("4K Monitor 27\"");
        monitor.setDescription("Ultra-sharp 27-inch 4K IPS display with 144Hz refresh rate and USB-C connectivity.");
        monitor.setPrice(new BigDecimal("549.00"));
        monitor.setCostPrice(new BigDecimal("310.00"));
        monitor.setMarginPercentage(new BigDecimal("43.53"));
        monitor.setSku("MON-4K-001");
        monitor.setInternalSku("INT-MON-2024-001");
        monitor.setCategory("electronics");
        monitor.setAvailable(false);
        productRepository.save(monitor);
    }

    private void seedAnnouncements() {
        if (announcementRepository.count() > 0) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());

        Announcement info = new Announcement();
        info.setTitle("Scheduled Maintenance Window");
        info.setMessage("The system will undergo scheduled maintenance on Saturday from 02:00 to 04:00 UTC. All services will be unavailable during this window.");
        info.setType(AnnouncementType.INFO);
        info.setPublishedAt(now.minusDays(1));
        info.setExpiresAt(now.plusDays(5));
        announcementRepository.save(info);

        Announcement warning = new Announcement();
        warning.setTitle("Deprecation Notice: Legacy API v0");
        warning.setMessage("The legacy API v0 endpoints will be removed on December 31st. Please migrate to the v1 API as soon as possible.");
        warning.setType(AnnouncementType.WARNING);
        warning.setPublishedAt(now.minusDays(7));
        warning.setExpiresAt(now.plusMonths(3));
        announcementRepository.save(warning);

        Announcement critical = new Announcement();
        critical.setTitle("Critical Security Patch Applied");
        critical.setMessage("A critical security vulnerability has been patched in the authentication module. All users are required to re-authenticate.");
        critical.setType(AnnouncementType.CRITICAL);
        critical.setPublishedAt(now.minusHours(2));
        announcementRepository.save(critical);
    }

    private void seedEmployees() {
        if (employeeRepository.count() > 0) {
            return;
        }

        Long engineeringId = departmentRepository.findByName("Engineering").map(Department::getId).orElseThrow();
        Long designId = departmentRepository.findByName("Design").map(Department::getId).orElseThrow();

        Employee engineer = new Employee();
        engineer.setFullName("Ada Lovelace");
        engineer.setDepartmentId(engineeringId);
        engineer.setEmail("ada@example.com");
        engineer.setSalary(new BigDecimal("125000.00"));
        engineer.setPerformanceNotes("Top performer; leads the DAL framework initiative.");
        employeeRepository.save(engineer);

        Employee designer = new Employee();
        designer.setFullName("Grace Hopper");
        designer.setDepartmentId(designId);
        designer.setEmail("grace@example.com");
        designer.setSalary(new BigDecimal("118000.00"));
        designer.setPerformanceNotes("Drives the design system; mentors juniors.");
        employeeRepository.save(designer);
    }

    private void seedTranslations() {
        if (translationRepository.count() > 0) {
            return;
        }
        translationRepository.save(translation("greeting", "en", "Hello"));
        translationRepository.save(translation("greeting", "it", "Ciao"));
        translationRepository.save(translation("farewell", "en", "Goodbye"));
    }

    private Translation translation(String messageKey, String locale, String value) {
        Translation translation = new Translation();
        translation.setId(new TranslationId(messageKey, locale));
        translation.setValue(value);
        return translation;
    }
}
