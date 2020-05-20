package sql;

import model.User;
import org.apache.ibatis.annotations.Param;

/**
 * @author Denis Danilin | denis@danilin.name
 * 01.05.2020
 * tfs ☭ sweat and blood
 */
public interface UserMapper {
    User selectUser(long id);

    void insertUser(User user);

    void update(User user);
}
