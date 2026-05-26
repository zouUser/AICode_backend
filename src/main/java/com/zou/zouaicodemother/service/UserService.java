package com.zou.zouaicodemother.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.zou.zouaicodemother.model.dto.user.UserQueryRequest;
import com.zou.zouaicodemother.model.entity.User;
import com.zou.zouaicodemother.model.vo.LoginUserVO;
import com.zou.zouaicodemother.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 用户 服务层。
 *
 * @author zou
 */
public interface UserService extends IService<User> {

	/**
	 * 用户注册
	 *
	 * @param userAccount   用户账户
	 * @param userPassword  用户密码
	 * @param checkPassword 校验密码
	 * @return 新用户 id
	 */
	long userRegister(String userAccount, String userPassword, String checkPassword);

	LoginUserVO getLoginUserVO(User user);

	/**
	 * 用户登录
	 *
	 * @param userAccount  用户账户
	 * @param userPassword 用户密码
	 * @param request
	 * @return 脱敏后的用户信息
	 */
	LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

	/**
	 * 用户注销
	 *
	 * @param request
	 * @return
	 */
	boolean userLogout(HttpServletRequest request);

	/**
	 * 获取当前登录用户
	 *
	 * @param request
	 * @return
	 */
	User getLoginUser(HttpServletRequest request);

	public UserVO getUserVO(User user);

	public List<UserVO> getUserVOList(List<User> userList);

	public String getEncryptPassword(String userPassword);

	public QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest);
}
