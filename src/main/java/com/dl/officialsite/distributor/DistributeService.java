package com.dl.officialsite.distributor;

import com.dl.officialsite.common.constants.Constants;
import com.dl.officialsite.common.enums.CodeEnums;
import com.dl.officialsite.common.enums.DistributeClaimerStatusEnums;
import com.dl.officialsite.common.enums.DistributeStatusEnums;
import com.dl.officialsite.common.enums.TokenStatusEnums;
import com.dl.officialsite.common.exception.BizException;
import com.dl.officialsite.common.utils.UserSecurityUtils;
import com.dl.officialsite.config.ChainConfig;
import com.dl.officialsite.config.ConstantConfig;
import com.dl.officialsite.distributor.distributeClaimer.DistributeClaimer;
import com.dl.officialsite.distributor.distributeClaimer.DistributeClaimerManager;
import com.dl.officialsite.distributor.distributeClaimer.DistributeClaimerRepository;
import com.dl.officialsite.distributor.vo.DistributeInfoVo;
import com.dl.officialsite.distributor.vo.GetDistributeByPageReqVo;
import com.dl.officialsite.distributor.vo.GetDistributeClaimerRspVo;
import com.dl.officialsite.member.Member;
import com.dl.officialsite.member.MemberManager;
import com.dl.officialsite.member.MemberRepository;
import com.dl.officialsite.redpacket.RedPacketRepository;
import com.dl.officialsite.tokenInfo.TokenInfo;
import com.dl.officialsite.tokenInfo.TokenInfoManager;
import com.dl.officialsite.tokenInfo.TokenInfoRepository;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cn.hutool.core.lang.Pair;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.transaction.Transactional;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Hash;

@Service
@Slf4j
@Configuration
public class DistributeService {

    @Autowired
    private RedPacketRepository redPacketRepository;

    @Autowired
    private ChainConfig chainConfig;

    public CloseableHttpClient httpClient = HttpClients.createDefault();

    private String lastUpdateTimestamp = "";

    @Autowired
    private DistributeRepository distributeRepository;

    @Autowired
    private DistributeClaimerRepository distributeClaimerRepository;

    @Autowired
    private MemberManager memberManager;

    @Autowired
    private TokenInfoManager tokenInfoManager;

    @Autowired
    private DistributeManager distributeManager;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TokenInfoRepository tokenInfoRepository;

    @Autowired
    private DistributeClaimerManager distributeClaimerManager;

    @Autowired
    private ConstantConfig constantConfig;

    @Scheduled(cron = "${jobs.distribute.corn:0/10 * * * * ?}")
    public void updateDistributeStatus() {
        log.info("schedule task begin --------------------- ");
        for (String chainId : chainConfig.getIds()) {
            try {
                updateDistributeStatusByChainId(chainId);
            } catch (Exception e) {
                e.printStackTrace();
                log.error("updateDistributeStatusByChainId:  " + chainId + " error:" + e.getMessage());
            }
        }
    }

    private void updateDistributeStatusByChainId(String chainId) throws IOException {
        log.info("chain_id " + chainId);
        HttpEntity entity = getHttpEntityFromChain(chainId);
        if (entity != null) {
            String jsonResponse = EntityUtils.toString(entity);

            if (jsonResponse.contains("errors")) {
                log.info("response from the graph: chainId{}, data {} ", chainId, jsonResponse);
                return;
            }
            JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
            JsonObject data = jsonObject.getAsJsonObject("data");
            JsonArray distributesArray = data.getAsJsonArray("distributes");
            JsonArray lastupdatesArray = data.getAsJsonArray("lastupdates");
            log.info("lastupdatesArray" + lastupdatesArray.toString());

            if (lastupdatesArray.size() != 0) {
                String lastTimestampFromGraph = lastupdatesArray.get(0).getAsJsonObject().get("lastupdateTimestamp")
                        .getAsString();

                if (Objects.equals(lastTimestampFromGraph, lastUpdateTimestamp)) {
                    return;
                } else {
                    lastUpdateTimestamp = lastTimestampFromGraph;
                }
            }

            List<DistributeInfo> distributeList = distributeManager.findUnfinishedDistributeByChainId(chainId);
            log.info("distributeList size " + distributeList.size());
            for (int i = 0; i < distributesArray.size(); i++) {
                // Access each element in the array
                JsonObject distributeObject = distributesArray.get(i).getAsJsonObject();

                String id = distributeObject.get("id").getAsString();
                for (int j = 0; j < distributeList.size(); j++) {
                    DistributeInfo distribute = distributeList.get(j);

                    if (!Objects.equals(distribute.getId(), id)) {
                        continue;
                    }

                    //// 0 uncompleted 1 completed 2 overtime 3 refund
                    Boolean allClaimed = distributeObject.get("allClaimed").getAsBoolean();
                    Boolean refunded = distributeObject.get("refunded").getAsBoolean();
                    log.info("****** refunded" + refunded);
                    log.info("****** allClaimed" + allClaimed);
                    if (distribute.getExpireTime() < System.currentTimeMillis() / 1000) {
                        distribute.setStatus(DistributeStatusEnums.TIME_OUT.getData());
                    }
                    if (allClaimed) {
                        distribute.setStatus(DistributeStatusEnums.COMPLETED.getData());
                    }
                    if (refunded) {
                        distribute.setStatus(DistributeStatusEnums.REFUND.getData());
                    }
                    distributeRepository.save(distribute);

                    JsonArray claimers = distributeObject.getAsJsonArray("claimers");
                    // ArrayList<String> claimersList = new ArrayList<>();
                    // ArrayList<String> claimedValueList = new ArrayList<>();
                    for (int k = 0; k < claimers.size(); k++) {
                        String claimer = claimers.get(k).getAsJsonObject().get("claimer").getAsString();
                        String claimedValue = claimers.get(k).getAsJsonObject().get("claimedValue").getAsString();

                        // check member
                        Member claimerRow = memberManager.getMemberByAddress(claimer);
                        if (Objects.isNull(claimerRow)) {
                            log.warn("invalid claimMember:{} claimedValue:{}", claimer, claimedValue);
                            continue;
                        }

                        // check claimerRow
                        Optional<DistributeClaimer> opRsp = distributeClaimerRepository
                                .findByDistributeAndClaimer(distribute.getId(), claimerRow.getId());
                        if (!opRsp.isPresent()) {
                            log.warn("invalid distribute:${} claimMember:{}", distribute.getId(), claimer);
                            continue;
                        }

                        // check amount
                        DistributeClaimer updateRow = opRsp.get();
                        if (updateRow.getDistributeAmount().toString() != claimedValue) {
                            log.warn("distribute:${} claimMember:{} configAmount:{} claimed:${}", distribute.getId(),
                                    claimer, updateRow.getDistributeAmount(), claimedValue);
                        }
                        updateRow.setDistributeAmount(BigDecimal.valueOf(Long.valueOf(claimedValue)));

                        // check status
                        if (updateRow.getStatus().toString() != DistributeClaimerStatusEnums.UN_CLAIM.getData()
                                .toString()) {
                            log.warn("distribute:${} claimMember:{}  claimed:${} dataStatus:{}", distribute.getId(),
                                    claimer, claimedValue, updateRow.getStatus().toString());
                        }
                        updateRow.setStatus(DistributeClaimerStatusEnums.CLAIMED.getData());

                        distributeClaimerRepository.save(updateRow);
                    }
                    // distribute.setClaimedAddress(claimersList);
                    // distribute.setClaimedValues(claimedValueList);

                }
            }
        }

    }

    private HttpEntity getHttpEntityFromChain(String chainId) throws IOException {
        HttpPost request = null;
        switch (chainId) {
            case Constants.CHAIN_ID_OP: // op
                request = new HttpPost("https://api.studio.thegraph.com/query/64274/optimism/version/latest");
                break;
            case Constants.CHAIN_ID_SEPOLIA: // sepolia
                request = new HttpPost("https://api.studio.thegraph.com/query/64274/sepolia/version/latest");
                break;
            case Constants.CHAIN_ID_SCROLL: // scrool
                request = new HttpPost("https://api.studio.thegraph.com/query/64274/scroll/version/latest");
                break;
            case Constants.CHAIN_ID_ARBITRUM: // arbitrum
                request = new HttpPost("https://api.studio.thegraph.com/query/64274/arbitrum/version/latest");
                break;

        }
        request.setHeader("Content-Type", "application/json");
        // Define your GraphQL query
        long currentTimeMillis = System.currentTimeMillis();
        long time = currentTimeMillis / 1000 - 3600 * 24 * 90;
        // time = Math.max(time, 1703751860);
        String creationTimeGtValue = String.valueOf(time);

        String graphQL = "\" {" +
                "  distributes (where: { creationTime_gt: " + creationTimeGtValue + " }) {" +
                "    id     " +
                "    refunded   " +
                "   lock " +
                "    name       " +
                "    creationTime   " +
                "    allClaimed  " +
                "    claimers {" +
                "    claimer" +
                "    claimedValue " +
                "    }" +
                " }" +
                "  lastupdates (orderBy : lastupdateTimestamp , orderDirection: desc) { lastupdateTimestamp } " +

                "}\"";

        String query = "{ \"query\": " +
                graphQL +
                " }";

        request.setEntity(new StringEntity(query));
        HttpResponse response = httpClient.execute(request);
        // System.out.println("response" + response);
        HttpEntity entity = response.getEntity();
        return entity;
    }

    // create new distribute
    @Transactional(rollbackOn = Exception.class)
    public DistributeInfo createDistribute(DistributeInfoVo param) {
        log.info("[createDistribute] param : ", String.valueOf(param));

        // current user TODO test.dev
        String creatorAddress = "0x1F7b953113f4dFcBF56a1688529CC812865840e2";
        if (constantConfig.getLoginFilter())
            creatorAddress = UserSecurityUtils.getUserLogin().getAddress();
        Member member = this.memberManager.requireMemberAddressExist(creatorAddress);
        param.setCreatorId(member.getId());
        // check ID
        if (!Objects.isNull(param.getId()))
            throw new BizException(CodeEnums.ID_NEED_EMPTY);
        // check chain
        if (!Arrays.asList(chainConfig.getIds()).contains(String.valueOf(param.getChainId())))
            throw new BizException(CodeEnums.INVALID_CHAIN_ID);

        // check distribute claimer
        Set<String> claimedSet = new HashSet<>(param.getClaimedAddress());
        if (claimedSet.size() != param.getClaimedAddress().size())
            throw new BizException(CodeEnums.DUPLICATE_CLAIMER);
        if (claimedSet.size() != param.getClaimedValues().size())
            throw new BizException(CodeEnums.SIZE_NOT_MATCH);
        // check and save token
        Long tokenId = param.getTokenId();
        if (null == tokenId) {
            Optional<TokenInfo> tokenOptional = this.tokenInfoRepository.findByChainAndAddress(param.getChainId(),
                    param.getToken());
            if (tokenOptional.isPresent()) {
                tokenId = tokenOptional.get().getId();
            } else {
                TokenInfo newToken = new TokenInfo();
                newToken.setChainId(param.getChainId());
                newToken.setTokenAddress(param.getToken());
                newToken.setTokenName(param.getTokenName());
                newToken.setTokenDecimal(param.getTokenDecimal());
                newToken.setTokenSymbol(param.getTokenSymbol());
                newToken.setStatus(TokenStatusEnums.NORMAL.getData());

                TokenInfo tokenInfo = tokenInfoRepository.save(newToken);
                tokenId = tokenInfo.getId();
            }
        } else {
            tokenInfoManager.requireIdIsValid(tokenId);
        }

        // save distribute
        DistributeInfo newDistributeInfo = new DistributeInfo();
        BeanUtils.copyProperties(param, newDistributeInfo);
        newDistributeInfo.setTokenId(tokenId);
        newDistributeInfo.setStatus(DistributeStatusEnums.UN_COMPLETED.getData());
        DistributeInfo savedDistribute = distributeRepository.save(newDistributeInfo);

        // check and save claimer
        for (int i = 0; i < claimedSet.size(); i++) {
            String claimer = param.getClaimedAddress().get(i);
            BigDecimal value = param.getClaimedValues().get(i);
            // check member
            Member claimerMember = memberManager.requireMemberAddressExist(claimer);

            // save claimer
            DistributeClaimer newRow = new DistributeClaimer();
            newRow.setDistributeId(savedDistribute.getId());
            newRow.setChainId(savedDistribute.getChainId());
            newRow.setClaimerId(claimerMember.getId());
            newRow.setDistributeAmount(value);
            newRow.setStatus(DistributeClaimerStatusEnums.CREATING.getData());
            distributeClaimerRepository.save(newRow);
        }

        return queryDistributeDetail(savedDistribute.getId());
    }

    // update distribute
    public void deleteDistribute(Long id) {
        log.info("[deleteDistribute] id :{} ", id);

        // check id
        DistributeInfo distributeInfo = distributeManager.requireIdIsValid(id);

        // current user TODO test.dev
        String creatorAddress = "0x1F7b953113f4dFcBF56a1688529CC812865840e2";
        if (constantConfig.getLoginFilter())
            creatorAddress = UserSecurityUtils.getUserLogin().getAddress();
        Member member = this.memberManager.requireMemberAddressExist(creatorAddress);
        if (distributeInfo.getCreatorId() != member.getId())
            throw new BizException(CodeEnums.ONLY_CREATOE);

        // check status
        if (distributeInfo.getStatus() != DistributeStatusEnums.UN_COMPLETED.getData())
            throw new BizException(CodeEnums.NOT_SUPPORT_MODIFY);

        // delete
        distributeRepository.deleteById(id);
    }

    // query detail
    @Transactional(rollbackOn = Exception.class)
    public DistributeInfoVo queryDistributeDetail(Long id) {
        log.info("[queryDistributeDetail] id :{} ", id);

        // check id
        DistributeInfo distributeInfo = distributeManager.requireIdIsValid(id);

        return convertDistributeInfoToDistributeInfoVo(distributeInfo);
    }

    // query by page
    public Page<DistributeInfoVo> queryDistributeByPage(GetDistributeByPageReqVo param) {
        log.info("[queryDistributeByPage]");

        Pageable pageable = PageRequest.of(param.getPageNumber() - 1, param.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));

        Specification<DistributeInfo> queryParam = new Specification<DistributeInfo>() {
            @Override
            public Predicate toPredicate(Root<DistributeInfo> root, CriteriaQuery<?> criteriaQuery,
                    CriteriaBuilder criteriaBuilder) {
                List<Predicate> predicates = new ArrayList<>();
                if (param.getId() != null) {
                    log.info(String.valueOf(param.getId()));
                    predicates.add(criteriaBuilder.like(root.get("id"), "%" + param.getId() + "%"));
                }
                if (param.getName() != null) {
                    predicates.add(criteriaBuilder.like(root.get("name"), "%" + param.getName() + "%"));
                }
                if (param.getChainId() != null) {
                    predicates.add(criteriaBuilder.equal(root.get("chainId"), param.getChainId()));
                }
                if (param.getCreator() != null) {
                    Member member = memberManager.getMemberByAddress(param.getCreator());
                    String creatorAddress = Objects.isNull(member) ? null : member.getAddress();
                    predicates.add(criteriaBuilder.equal(root.get("creatorId"), creatorAddress));
                }
                if (param.getExpireTime() != null) {
                    predicates.add(criteriaBuilder.lessThan(root.get("expireTime"), param.getExpireTime()));
                }
                if (param.getCreateTime() != null) {
                    predicates.add(criteriaBuilder.greaterThan(root.get("createTime"), param.getCreateTime()));
                }
                if (param.getStatus() != null) {
                    predicates.add(criteriaBuilder.equal(root.get("status"), param.getStatus()));
                }
                return criteriaBuilder.and(predicates.toArray(new Predicate[predicates.size()]));
            }
        };


        Page<DistributeInfo> dbRsp = distributeRepository.findAll(queryParam, pageable);
        List<DistributeInfoVo> voList = dbRsp.getContent().stream()
                .map(this::convertDistributeInfoToDistributeInfoVo)
                .collect(Collectors.toList());
        return new PageImpl<>(voList, pageable, dbRsp.getTotalElements());


        }

    public DistributeInfoVo convertDistributeInfoToDistributeInfoVo(DistributeInfo distribute) {

        // copy attribute
        DistributeInfoVo distributeInfoVo = new DistributeInfoVo();
        BeanUtils.copyProperties(distribute, distributeInfoVo);

        // query member
        Optional<Member> memberOptional = memberRepository.findById(distribute.getCreatorId());
        memberOptional.ifPresent(memberRow -> distributeInfoVo.setCreator(memberRow.getAddress()));

        // query token
        Optional<TokenInfo> tokenOptional = tokenInfoRepository.findById(distribute.getTokenId());
        tokenOptional.ifPresent(row -> {
            distributeInfoVo.setToken(row.getTokenAddress());
            distributeInfoVo.setTokenDecimal(row.getTokenDecimal());
            distributeInfoVo.setTokenName(row.getTokenName());
            distributeInfoVo.setTokenSymbol(row.getTokenSymbol());
        });

        // query claimer
        Pair<List<String>, List<BigDecimal>> pairRsp = distributeClaimerManager
                .getAllClaimerAndValueAsListByDistributeId(distribute.getId());
        distributeInfoVo.setClaimedAddress(pairRsp.getKey());
        distributeInfoVo.setClaimedValues(pairRsp.getValue());
        return distributeInfoVo;
    }
}