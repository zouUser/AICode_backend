package com.zou.zouaicodemother.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.zou.zouaicodemother.exception.BusinessException;
import com.zou.zouaicodemother.exception.ErrorCode;
import com.zou.zouaicodemother.model.dto.user.UserQueryRequest;
import com.zou.zouaicodemother.model.entity.User;
import com.zou.zouaicodemother.mapper.UserMapper;
import com.zou.zouaicodemother.model.enums.UserRoleEnum;
import com.zou.zouaicodemother.model.vo.LoginUserVO;
import com.zou.zouaicodemother.model.vo.UserVO;
import com.zou.zouaicodemother.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static com.zou.zouaicodemother.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户 服务层实现。
 *
 * @author zou
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>  implements UserService {

	@Override
	public UserVO getUserVO(User user) {
		if (user == null) {
			return null;
		}
		UserVO userVO = new UserVO();
		BeanUtil.copyProperties(user, userVO);
		return userVO;
	}

	@Override
	public List<UserVO> getUserVOList(List<User> userList) {
		if (CollUtil.isEmpty(userList)) {
			return new ArrayList<>();
		}
		return userList.stream().map(this::getUserVO).collect(Collectors.toList());
	}

	@Override
	public boolean userLogout(HttpServletRequest request) {
		// 先判断是否已登录
		Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
		if (userObj == null) {
			throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
		}
		// 移除登录态
		request.getSession().removeAttribute(USER_LOGIN_STATE);
		return true;
	}

	@Override
	public User getLoginUser(HttpServletRequest request) {
		// 先判断是否已登录
		Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
		User currentUser = (User) userObj;
		if (currentUser == null || currentUser.getId() == null) {
			throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
		}
		// 从数据库查询（追求性能的话可以注释，直接返回上述结果）
		long userId = currentUser.getId();
		currentUser = this.getById(userId);
		if (currentUser == null) {
			throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
		}
		return currentUser;
	}

	//注册
	@Override
	public long userRegister(String userAccount, String userPassword, String checkPassword) {
		// 1. 校验
		if (StrUtil.hasBlank(userAccount, userPassword, checkPassword)) {
			throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
		}
		if (userAccount.length() < 4) {
			throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
		}
		if (userPassword.length() < 8 || checkPassword.length() < 8) {
			throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
		}
		if (!userPassword.equals(checkPassword)) {
			throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
		}
		// 2. 检查是否重复
		QueryWrapper queryWrapper = new QueryWrapper();
		queryWrapper.eq("userAccount", userAccount);
		long count = this.mapper.selectCountByQuery(queryWrapper);
		if (count > 0) {
			throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
		}
		// 3. 加密
		String encryptPassword = getEncryptPassword(userPassword);
		// 4. 插入数据
		User user = new User();
		user.setUserAccount(userAccount);
		user.setUserPassword(encryptPassword);
		user.setUserName("无名");
		user.setUserRole(UserRoleEnum.USER.getValue());
		user.setUserAvatar(generateRandomAvatar());
		boolean saveResult = this.save(user);
		if (!saveResult) {
			throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
		}
		return user.getId();
	}

	@Override
	public LoginUserVO getLoginUserVO(User user) {
		if (user == null) {
			return null;
		}
		LoginUserVO loginUserVO = new LoginUserVO();
		BeanUtil.copyProperties(user, loginUserVO);
		return loginUserVO;
	}

	//登录
	@Override
	public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
		// 1. 校验
		if (StrUtil.hasBlank(userAccount, userPassword)) {
			throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
		}
		if (userAccount.length() < 4) {
			throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号错误");
		}
		if (userPassword.length() < 8) {
			throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
		}
		// 2. 加密
		String encryptPassword = getEncryptPassword(userPassword);
		// 查询用户是否存在
		QueryWrapper queryWrapper = new QueryWrapper();
		queryWrapper.eq("userAccount", userAccount);
		queryWrapper.eq("userPassword", encryptPassword);
		User user = this.mapper.selectOneByQuery(queryWrapper);
		// 用户不存在
		if (user == null) {
			throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
		}
		// 3. 记录用户的登录态
		request.getSession().setAttribute(USER_LOGIN_STATE, user);
		// 4. 获得脱敏后的用户信息
		return this.getLoginUserVO(user);
	}

	//加密
	@Override
	public String getEncryptPassword(String userPassword) {
		// 盐值，混淆密码
		final String SALT = "zouAI";
		return DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
	}

	@Override
	public QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest) {
		if (userQueryRequest == null) {
			throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
		}
		Long id = userQueryRequest.getId();
		String userAccount = userQueryRequest.getUserAccount();
		String userName = userQueryRequest.getUserName();
		String userProfile = userQueryRequest.getUserProfile();
		String userRole = userQueryRequest.getUserRole();
		String sortField = userQueryRequest.getSortField();
		String sortOrder = userQueryRequest.getSortOrder();
		return QueryWrapper.create()
				.eq("id", id)
				.eq("userRole", userRole)
				.like("userAccount", userAccount)
				.like("userName", userName)
				.like("userProfile", userProfile)
				.orderBy(sortField, "ascend".equals(sortOrder));
	}

	private static final String[] AVATAR_STYLES = {
			"avataaars",
			"bottts",
			"pixel-art",
			"identicon",
			"initials",
			"shapes"
	};

	private static final String DICEBEAR_BASE_URL = "https://api.dicebear.com/7.x/";

	private String generateRandomAvatar() {
		Random random = new Random();
		String style = AVATAR_STYLES[random.nextInt(AVATAR_STYLES.length)];
		String seed = String.valueOf(System.currentTimeMillis()) + random.nextInt(10000);
		return DICEBEAR_BASE_URL + style + "/svg?seed=" + seed;
	}

}
