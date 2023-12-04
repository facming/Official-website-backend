package com.dl.officialsite.sharing.service;

import com.dl.officialsite.sharing.model.pojo.SharingPojo;
import com.dl.officialsite.sharing.model.req.CreateSharingReq;
import com.dl.officialsite.sharing.model.req.PreCheckSharingRewardReq;
import com.dl.officialsite.sharing.model.req.UpdateSharingReq;
import com.dl.officialsite.sharing.model.resp.AllSharingResp;
import com.dl.officialsite.sharing.model.resp.ClaimSharingRewardResp;
import com.dl.officialsite.sharing.model.resp.PreCheckSharingRewardResp;
import com.dl.officialsite.sharing.model.resp.SharingByUserResp;

/**
 * 分享人的分享管理功能
 */
public interface ISharingService {

    //————————————————————————————————————分享人相关功能————————————————————————————————————
    /**
     * 创建分享
     * @param req
     * @return
     */
    long createSharing(CreateSharingReq req);

    /**
     * 修改分享
     */
    void updateSharing(UpdateSharingReq req);

    /**
     * 删除分享
     */
    void deleteSharing(long shareId);

    /**
     * 预先查看分享奖励信息供分享人确认
     * @param sharingId
     * @return
     */
    PreCheckSharingRewardResp preCheckSharingReward(long sharingId);

    /**
     * 领取奖励
     * @param sharingId
     * @return
     */
    ClaimSharingRewardResp claimSharingReward(long sharingId);

    //————————————————————————————————————网站读者相关功能————————————————————————————————————
    /**
     * 查看全部分享
     * @return
     */
    AllSharingResp loadSharing(int pageNo, int pageSize);

    /**
     * 查看分享内容
     * @param shareId
     * @return
     */
    SharingPojo querySharing(long shareId);

    /**
     * 查看用户的分享
     * @param uid
     * @return
     */
    SharingByUserResp loadSharingByUser(long uid);
}
