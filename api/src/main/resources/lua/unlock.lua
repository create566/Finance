-- 释放分布式锁：仅当 key 的 value 仍是自己的 token 时才删除。
-- GET 和 DEL 必须在一个脚本里完成，分开调用存在竞态：
-- 取到 value 判断通过后、DEL 之前锁可能已过期并被别的实例获取，此时 DEL 会误删他人的锁。
if redis.call('get', KEYS[1]) == ARGV[1] then
    return redis.call('del', KEYS[1])
else
    return 0
end
