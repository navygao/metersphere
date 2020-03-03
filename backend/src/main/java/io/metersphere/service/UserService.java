package io.metersphere.service;

import io.metersphere.base.domain.*;
import io.metersphere.base.mapper.*;
import io.metersphere.base.mapper.ext.ExtUserRoleMapper;
import io.metersphere.commons.exception.MSException;
import io.metersphere.commons.utils.CodingUtil;
import io.metersphere.controller.request.member.AddMemberRequest;
import io.metersphere.controller.request.member.QueryMemberRequest;
import io.metersphere.controller.request.organization.AddOrgMemberRequest;
import io.metersphere.controller.request.organization.QueryOrgMemberRequest;
import io.metersphere.dto.OrganizationMemberDTO;
import io.metersphere.dto.UserDTO;
import io.metersphere.dto.UserRoleDTO;
import io.metersphere.dto.UserRoleHelpDTO;
import io.metersphere.user.SessionUser;
import io.metersphere.user.SessionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class UserService {

    @Resource
    private UserMapper userMapper;
    @Resource
    private RoleMapper roleMapper;
    @Resource
    private UserRoleMapper userRoleMapper;
    @Resource
    private ExtUserRoleMapper extUserRoleMapper;
    @Resource
    private OrganizationMapper organizationMapper;
    @Resource
    private WorkspaceMapper workspaceMapper;

    public UserDTO insert(User user) {
        checkUserParam(user);
        createUser(user);
        return getUserDTO(user.getId());
    }

    private void checkUserParam(User user) {
        if (StringUtils.isBlank(user.getName())) {
            MSException.throwException("user_name_empty");
        }

        if (StringUtils.isBlank(user.getEmail())) {
            MSException.throwException("user_email_empty");
        }
        // password
    }

    private void createUser(User userRequest) {
        User user = new User();
        BeanUtils.copyProperties(userRequest, user);
        user.setCreateTime(System.currentTimeMillis());
        user.setUpdateTime(System.currentTimeMillis());
        // 默认1:启用状态
        user.setStatus("1");

        UserExample userExample = new UserExample();
        UserExample.Criteria criteria = userExample.createCriteria();
        criteria.andEmailEqualTo(user.getEmail());
        List<User> userList = userMapper.selectByExample(userExample);
        if (!CollectionUtils.isEmpty(userList)) {
            MSException.throwException("user_email_is_exist");
        }
        userMapper.insertSelective(user);
    }

    public UserDTO getUserDTO(String userId) {

        User user = userMapper.selectByPrimaryKey(userId);
        if (user == null) {
            return null;
        }
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);
        //
        UserRoleExample userRoleExample = new UserRoleExample();
        userRoleExample.createCriteria().andUserIdEqualTo(userId);
        List<UserRole> userRoleList = userRoleMapper.selectByExample(userRoleExample);

        if (CollectionUtils.isEmpty(userRoleList)) {
            return userDTO;
        }
        // 设置 user_role
        userDTO.setUserRoles(userRoleList);

        List<String> roleIds = userRoleList.stream().map(UserRole::getRoleId).collect(Collectors.toList());

        RoleExample roleExample = new RoleExample();
        roleExample.createCriteria().andIdIn(roleIds);

        List<Role> roleList = roleMapper.selectByExample(roleExample);
        userDTO.setRoles(roleList);

        return userDTO;
    }

    public List<User> getUserList() {
        return userMapper.selectByExample(null);
    }

    public void deleteUser(String userId) {
        userMapper.deleteByPrimaryKey(userId);
    }

    public void updateUser(User user) {
        user.setUpdateTime(System.currentTimeMillis());
        userMapper.updateByPrimaryKeySelective(user);
    }

    public List<Role> getUserRolesList(String userId) {
        UserRoleExample userRoleExample = new UserRoleExample();
        userRoleExample.createCriteria().andUserIdEqualTo(userId);
        List<UserRole> userRolesList = userRoleMapper.selectByExample(userRoleExample);
        List<String> roleIds = userRolesList.stream().map(UserRole::getRoleId).collect(Collectors.toList());
        RoleExample roleExample = new RoleExample();
        roleExample.createCriteria().andIdIn(roleIds);
        return roleMapper.selectByExample(roleExample);
    }

    public List<UserRoleDTO> getUserRoleList(String userId) {
        if (StringUtils.isEmpty(userId)) {
            return new ArrayList<>();
        }
        return convertUserRoleDTO(extUserRoleMapper.getUserRoleHelpList(userId));
    }

    private List<UserRoleDTO> convertUserRoleDTO(List<UserRoleHelpDTO> helpDTOList) {
        StringBuilder buffer = new StringBuilder();

        Map<String, UserRoleDTO> roleMap = new HashMap<>();

        List<UserRoleDTO> resultList = new ArrayList<>();

        List<UserRoleDTO> otherList = new ArrayList<>();

        Set<String> orgSet = new HashSet<>();

        Set<String> workspaceSet = new HashSet<>();

        for (UserRoleHelpDTO helpDTO : helpDTOList) {
            UserRoleDTO userRoleDTO = roleMap.get(helpDTO.getSourceId());

            if (userRoleDTO == null) {
                userRoleDTO = new UserRoleDTO();

                if (!StringUtils.isEmpty(helpDTO.getParentId())) {
                    workspaceSet.add(helpDTO.getParentId());
                    userRoleDTO.setType("workspace");
                } else {
                    orgSet.add(helpDTO.getSourceId());
                    userRoleDTO.setType("organization");
                }

                userRoleDTO.setId(helpDTO.getSourceId());
                userRoleDTO.setRoleId(helpDTO.getRoleId());
                userRoleDTO.setName(helpDTO.getSourceName());
                userRoleDTO.setParentId(helpDTO.getParentId());
                userRoleDTO.setDesc(helpDTO.getRoleName());

            } else {
                userRoleDTO.setDesc(userRoleDTO.getDesc() + "," + helpDTO.getRoleName());
            }
            roleMap.put(helpDTO.getSourceId(), userRoleDTO);
        }

        if (!StringUtils.isEmpty(buffer.toString())) {
            UserRoleDTO dto = new UserRoleDTO();
            dto.setId("admin");
            dto.setType("admin");
            dto.setDesc(buffer.toString());
            resultList.add(dto);
        }

        for (String org : orgSet) {
            workspaceSet.remove(org);
        }

        List<UserRoleDTO> orgWorkSpace = new ArrayList<>(roleMap.values());

        if (!CollectionUtils.isEmpty(workspaceSet)) {
            for (String orgId : workspaceSet) {
                Organization organization = organizationMapper.selectByPrimaryKey(orgId);
                if (organization != null) {
                    UserRoleDTO dto = new UserRoleDTO();
                    dto.setId(orgId);
                    dto.setName(organization.getName());
                    dto.setSwitchable(false);
                    dto.setType("organization");
                    orgWorkSpace.add(dto);
                }
            }
        }

        orgWorkSpace.sort((o1, o2) -> {
            if (o1.getParentId() == null) {
                return -1;
            }

            if (o2.getParentId() == null) {
                return 1;
            }

            return o1.getParentId().compareTo(o2.getParentId());
        });
        resultList.addAll(orgWorkSpace);
        resultList.addAll(otherList);

        return resultList;
    }

    public void switchUserRole(UserDTO user, String sign, String sourceId) {
        User newUser = new User();
        if (StringUtils.equals("organization", sign)) {
            user.setLastOrganizationId(sourceId);
            user.setLastWorkspaceId("");
        }
        if (StringUtils.equals("workspace", sign)) {
            Workspace workspace = workspaceMapper.selectByPrimaryKey(sourceId);
            user.setLastOrganizationId(workspace.getOrganizationId());
            user.setLastWorkspaceId(sourceId);
        }
        BeanUtils.copyProperties(user, newUser);
        // 切换工作空间或组织之后更新 session 里的 user
        SessionUtils.putUser(SessionUser.fromUser(user));
        userMapper.updateByPrimaryKeySelective(newUser);
    }

    public User getUserInfo(String userId) {
        return userMapper.selectByPrimaryKey(userId);
    }

    public List<User> getMemberList(QueryMemberRequest request) {
        return extUserRoleMapper.getMemberList(request);
    }

    public void addMember(AddMemberRequest request) {
        if (!CollectionUtils.isEmpty(request.getUserIds())) {
            for (String userId : request.getUserIds()) {
                UserRoleExample userRoleExample = new UserRoleExample();
                userRoleExample.createCriteria().andUserIdEqualTo(userId).andSourceIdEqualTo(request.getWorkspaceId());
                List<UserRole> userRoles = userRoleMapper.selectByExample(userRoleExample);
                if (userRoles.size() > 0) {
                    User user = userMapper.selectByPrimaryKey(userId);
                    String username = user.getName();
                    MSException.throwException("The user [" + username + "] already exists in the current workspace！");
                } else {
                    for (String roleId : request.getRoleIds()) {
                        UserRole userRole = new UserRole();
                        userRole.setRoleId(roleId);
                        userRole.setSourceId(request.getWorkspaceId());
                        userRole.setUserId(userId);
                        userRole.setId(UUID.randomUUID().toString());
                        userRole.setUpdateTime(System.currentTimeMillis());
                        userRole.setCreateTime(System.currentTimeMillis());
                        userRoleMapper.insertSelective(userRole);
                    }
                }
            }
        }
    }

    public void deleteMember(String workspaceId, String userId) {
        UserRoleExample example = new UserRoleExample();
        example.createCriteria().andRoleIdLike("%test%")
                .andUserIdEqualTo(userId).andSourceIdEqualTo(workspaceId);
        userRoleMapper.deleteByExample(example);
    }

    public void addOrganizationMember(AddOrgMemberRequest request) {
        if (!CollectionUtils.isEmpty(request.getUserIds())) {
            for (String userId : request.getUserIds()) {
                UserRoleExample userRoleExample = new UserRoleExample();
                userRoleExample.createCriteria().andUserIdEqualTo(userId).andSourceIdEqualTo(request.getOrganizationId());
                List<UserRole> userRoles = userRoleMapper.selectByExample(userRoleExample);
                if (userRoles.size() > 0) {
                    User user = userMapper.selectByPrimaryKey(userId);
                    String username = user.getName();
                    MSException.throwException("The user [" + username + "] already exists in the current organization！");
                } else {
                    for (String roleId : request.getRoleIds()) {
                        UserRole userRole = new UserRole();
                        userRole.setId(UUID.randomUUID().toString());
                        userRole.setRoleId(roleId);
                        userRole.setSourceId(request.getOrganizationId());
                        userRole.setUserId(userId);
                        userRole.setUpdateTime(System.currentTimeMillis());
                        userRole.setCreateTime(System.currentTimeMillis());
                        userRoleMapper.insertSelective(userRole);
                    }
                }
            }
        }
    }

    public void delOrganizationMember(String organizationId, String userId) {
        UserRoleExample userRoleExample = new UserRoleExample();
        userRoleExample.createCriteria().andRoleIdLike("%org%").andUserIdEqualTo(userId).andSourceIdEqualTo(organizationId);
        userRoleMapper.deleteByExample(userRoleExample);
    }

    public List<User> getOrgMemberList(QueryOrgMemberRequest request) {
        return extUserRoleMapper.getOrgMemberList(request);
    }

    public boolean checkUserPassword(String userId, String password) {
        if (StringUtils.isBlank(userId)) {
            MSException.throwException("Username cannot be null");
        }
        if (StringUtils.isBlank(password)) {
            MSException.throwException("Password cannot be null");
        }
        UserExample example = new UserExample();
        example.createCriteria().andIdEqualTo(userId).andPasswordEqualTo(CodingUtil.md5(password));
        return userMapper.countByExample(example) > 0;
    }

    public List<OrganizationMemberDTO> getOrganizationMemberDTO(QueryOrgMemberRequest request) {
        return extUserRoleMapper.getOrganizationMemberDTO(request);
    }

    /**
     * 查询该组织外的其他用户列表
     */
    public List<User> getBesideOrgMemberList(String orgId) {
        return extUserRoleMapper.getBesideOrgMemberList(orgId);
    }
}
