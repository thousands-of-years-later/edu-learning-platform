package com.ruoyi.common.core.api;

/**
 * API 调用者上下文（ThreadLocal）
 *
 * 为什么需要它：
 *   /api/v1/** 在 Shiro 里整体是 anon（Token 校验由 ApiTokenFilter 负责），
 *   所以控制器里【没有 Shiro 登录态】，ShiroUtils.getUserId() 会是 null。
 *   过滤器把 Token 解析出的用户放这里，控制器只从这里取用户ID。
 *
 * 为什么用 ThreadLocal：
 *   一次请求从头到尾都在同一个 Tomcat 工作线程上，
 *   放 ThreadLocal 才能不靠方法参数把用户ID层层透传下去。
 *
 * 为什么必须 finally clear（重要）：
 *   Tomcat 的线程是复用的。一次请求结束后若不清理，下一个请求
 *   （很可能是【另一个用户】的请求）会读到上一个用户的身份 —— 这就是越权。
 *   所以 ApiTokenFilter 里是 try { ... } finally { ApiContext.clear(); }。
 *
 * @author edu
 */
public class ApiContext
{
    /**
     * 当前请求的调用者
     */
    private static final ThreadLocal<ApiUser> CONTEXT = new ThreadLocal<ApiUser>();

    /**
     * 设置当前请求的调用者（由 ApiTokenFilter 调用）
     */
    public static void setUser(ApiUser user)
    {
        CONTEXT.set(user);
    }

    /**
     * 当前请求的调用者，未通过 Token 校验时为 null
     */
    public static ApiUser getUser()
    {
        return CONTEXT.get();
    }

    /**
     * 当前调用者ID，未通过 Token 校验时为 null
     */
    public static Long getUserId()
    {
        ApiUser user = CONTEXT.get();
        return user == null ? null : user.getUserId();
    }

    /**
     * 当前调用者登录账号，未通过 Token 校验时为 null
     */
    public static String getLoginName()
    {
        ApiUser user = CONTEXT.get();
        return user == null ? null : user.getLoginName();
    }

    /**
     * 清理上下文
     *
     * 用 remove() 而不是 set(null)：remove() 才是真的把引用从这个线程上摘掉，
     * set(null) 会在 ThreadLocalMap 里留下一个 null 条目。
     */
    public static void clear()
    {
        CONTEXT.remove();
    }
}
