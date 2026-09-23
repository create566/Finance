package com.example.demo.service;

public interface BankDatabaseSyncService {

    void syncBindRecord(String bindCode, String agentUserId, String personalName,
                        String mobile, String idCard, String bankNo, String bankType);

    boolean checkBindCodeExists(String bindCode);
}