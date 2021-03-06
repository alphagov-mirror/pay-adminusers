package uk.gov.pay.adminusers.persistence.dao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.pay.adminusers.model.Role;
import uk.gov.pay.adminusers.persistence.entity.RoleEntity;

import java.util.Optional;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.adminusers.fixtures.RoleDbFixture.roleDbFixture;

public class RoleDaoIT extends DaoTestBase {

    private RoleDao roleDao;

    @BeforeEach
    public void before() {
        roleDao = env.getInstance(RoleDao.class);
    }

    @Test
    public void shouldFindARoleByRoleName() {

        Role role1 = roleDbFixture(databaseHelper).insertRole();
        Role role2 = roleDbFixture(databaseHelper).insertRole();

        Optional<RoleEntity> optionalRole1 = roleDao.findByRoleName(role1.getName());
        assertTrue(optionalRole1.isPresent());

        RoleEntity roleEntity = optionalRole1.get();
        assertThat(roleEntity.toRole(), is(role1));

        Optional<RoleEntity> optionalRole2 = roleDao.findByRoleName(role2.getName());
        assertThat(optionalRole2.get().toRole(), is(role2));
    }
}
