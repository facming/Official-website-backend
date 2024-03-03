package com.dl.officialsite.bounty;

import static com.dl.officialsite.common.constants.Constants.BOUNTY_MEMBER_MAP_STATUS_FINISH;
import static com.dl.officialsite.common.enums.CodeEnums.NOT_FOUND_BOUNTY;

import com.dl.officialsite.bounty.vo.ApplyBountyVo;
import com.dl.officialsite.bounty.vo.BountyMemberVo;
import com.dl.officialsite.bounty.vo.BountySearchVo;
import com.dl.officialsite.bounty.vo.BountyVo;
import com.dl.officialsite.bounty.vo.MyBountySearchVo;
import com.dl.officialsite.common.constants.Constants;
import com.dl.officialsite.common.exception.BizException;
import com.dl.officialsite.member.Member;
import com.dl.officialsite.member.MemberRepository;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import javax.persistence.criteria.Predicate;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * @ClassName BountyService
 * @Author jackchen
 * @Date 2024/1/26 19:15
 * @Description BountyService
 **/
@Service
public class BountyService {

    private final BountyRepository bountyRepository;

    private final BountyMemberMapRepository bountyMemberMapRepository;

    private final MemberRepository memberRepository;

    public BountyService(BountyRepository bountyRepository,
        BountyMemberMapRepository bountyMemberMapRepository, MemberRepository memberRepository) {
        this.bountyRepository = bountyRepository;
        this.bountyMemberMapRepository = bountyMemberMapRepository;
        this.memberRepository = memberRepository;
    }

    public BountyVo add(BountyVo bountyVo, String address) {
        Bounty bounty = new Bounty();
        BeanUtils.copyProperties(bountyVo, bounty);
        bounty.setCreator(address);
        bountyRepository.save(bounty);
        bountyVo.setId(bounty.getId());
        Member creatorInfo = memberRepository.findByAddress(bounty.getCreator()).orElse(null);
        bountyVo.setCreator(creatorInfo);
        return bountyVo;
    }

    public void update(BountyVo bountyVo, String address) {
        Bounty bounty = bountyRepository.findById(bountyVo.getId())
            .orElseThrow(
                () -> new BizException(NOT_FOUND_BOUNTY.getCode(), NOT_FOUND_BOUNTY.getMsg()));
        BeanUtils.copyProperties(bountyVo, bounty);
        bountyRepository.save(bounty);
    }

    public Page<BountyVo> search(BountySearchVo bountySearchVo, Pageable pageable) {
        Specification<Bounty> spec = Specification.where(hasCreator(bountySearchVo.getCreator()))
            .and(hasTitle(bountySearchVo.getTitle()))
            .and(hasStatus(bountySearchVo.getStatus()))
            .and(hasDeadLineBefore(bountySearchVo.getDeadLine()))
            .and(isNotStatus(5));

        Page<Bounty> bountyPage = bountyRepository.findAll(spec, pageable);
        return bountyPage.map(this::mapToBountyVo);
    }

    private Specification<Bounty> hasCreator(String creator) {
        return (root, query, criteriaBuilder) -> creator != null ?
            criteriaBuilder.equal(root.get("creator"), creator) : null;
    }

    private Specification<Bounty> hasTitle(String title) {
        return (root, query, criteriaBuilder) -> title != null ?
            criteriaBuilder.like(root.get("title"), "%" + title + "%") : null;
    }

    private Specification<Bounty> hasStatus(Integer status) {
        return (root, query, criteriaBuilder) -> status != null ?
            criteriaBuilder.equal(root.get("status"), status) : null;
    }

    private Specification<Bounty> hasDeadLineBefore(LocalDateTime deadline) {
        return (root, query, criteriaBuilder) -> deadline != null ?
            criteriaBuilder.lessThan(root.get("deadLine"), deadline) : null;
    }

    private Specification<Bounty> isNotStatus(Integer status) {
        return (root, query, criteriaBuilder) ->
            criteriaBuilder.notEqual(root.get("status"), status);
    }

    private BountyVo mapToBountyVo(Bounty bounty) {
        BountyVo bountyVo = new BountyVo();
        BeanUtils.copyProperties(bounty, bountyVo);
        List<BountyMemberMap> bountyMember = findBountyMemberMapByBountyId(bounty.getId());
        bountyVo.setBountyMemberMaps(bountyMember);
        Member creatorInfo = memberRepository.findByAddress(bounty.getCreator()).orElse(null);
        bountyVo.setCreator(creatorInfo);
        return bountyVo;
    }

    public void delete(Long id, String address) {
        Bounty bounty = bountyRepository.findById(id)
            .orElseThrow(
                () -> new BizException(NOT_FOUND_BOUNTY.getCode(), NOT_FOUND_BOUNTY.getMsg()));
        bounty.setStatus(Constants.BOUNTY_STATUS_DELETE);
        bountyRepository.save(bounty);
    }

    public BountyVo findByIdInternal(Long id) {
        Bounty bounty = bountyRepository.findById(id)
            .orElseThrow(
                () -> new BizException(NOT_FOUND_BOUNTY.getCode(), NOT_FOUND_BOUNTY.getMsg()));
        return mapToBountyVo(bounty);
    }

    public void apply(Long bountyId, String address) {
        Bounty bounty = bountyRepository.findById(bountyId)
            .orElseThrow(
                () -> new BizException(NOT_FOUND_BOUNTY.getCode(), NOT_FOUND_BOUNTY.getMsg()));
        if (bounty.getCreator().equals(address)) {
            throw new BizException("2002", "not apply bounty by creator");
        }
        bountyMemberMapRepository.findByBountyIdAndMemberAddress(bountyId, address)
            .ifPresent(bountyMemberMap -> {
                throw new BizException("2004", "bounty has been applied");

            });
        BountyMemberMap bountyMemberMap = new BountyMemberMap();
        bountyMemberMap.setBountyId(bountyId);
        bountyMemberMap.setMemberAddress(address);
        bountyMemberMap.setStatus(Constants.BOUNTY_MEMBER_MAP_STATUS_APPLY);
        bountyMemberMapRepository.save(bountyMemberMap);
    }

    public List<BountyMemberMap> findBountyMemberMapByBountyId(Long bounty) {
        return bountyMemberMapRepository.findAll(
            (Specification<BountyMemberMap>) (root, query, criteriaBuilder) -> {
                List<Predicate> predicates = new LinkedList<>();
                predicates.add(criteriaBuilder.equal(root.get("bountyId"), bounty));
                return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
            });
    }

    public Page<BountyVo> myBounty(MyBountySearchVo myBountySearchVo, Pageable pageable) {
        return bountyMemberMapRepository.findAll(
            (Specification<BountyMemberMap>) (root, query, criteriaBuilder) -> {
                List<Predicate> predicates = new LinkedList<>();
                predicates.add(criteriaBuilder.equal(root.get("memberAddress"),
                    myBountySearchVo.getMemberAddress()));
                if (myBountySearchVo.getStatus() != null) {
                    predicates.add(
                        criteriaBuilder.equal(root.get("status"), myBountySearchVo.getStatus()));
                }
                return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
            }, pageable).map(bountyMemberMap -> {
            Bounty bounty = bountyRepository.findById(bountyMemberMap.getBountyId())
                .orElseThrow(
                    () -> new BizException(NOT_FOUND_BOUNTY.getCode(), NOT_FOUND_BOUNTY.getMsg()));
            BountyVo bountyVo = new BountyVo();
            BeanUtils.copyProperties(bounty, bountyVo);
            Member creatorInfo = memberRepository.findByAddress(bounty.getCreator()).orElse(null);
            bountyVo.setCreator(creatorInfo);
            return bountyVo;
        });
    }

    public void approve(ApplyBountyVo applyBountyVo) {
        bountyMemberMapRepository.findByBountyIdAndMemberAddress(applyBountyVo.getBountyId(),
                applyBountyVo.getAddress())
            .ifPresent(bountyMemberMap -> {
                bountyMemberMap.setStatus(BOUNTY_MEMBER_MAP_STATUS_FINISH);
                bountyMemberMapRepository.save(bountyMemberMap);
            });
    }

    public Page<BountyMemberVo> findBountyMemberMapByBountyId(Long id, Pageable pageable) {
        return bountyMemberMapRepository.findAll(
            (Specification<BountyMemberMap>) (root, query, criteriaBuilder) -> {
                List<Predicate> predicates = new LinkedList<>();
                predicates.add(criteriaBuilder.equal(root.get("bountyId"), id));
                return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
            }, pageable).map(bountyMemberMap -> {
            BountyMemberVo bountyMemberVo = new BountyMemberVo();
            BeanUtils.copyProperties(bountyMemberMap, bountyMemberVo);
            memberRepository.findByAddress(bountyMemberMap.getMemberAddress())
                .ifPresent(bountyMemberVo::setMember);
            return bountyMemberVo;
        });
    }

    public Integer isApply(Long bountyId, String address) {
        return bountyMemberMapRepository.findByBountyIdAndMemberAddress(
            bountyId, address).map(BountyMemberMap::getStatus).orElse(null);
    }

    public void link(BountyVo bountyVo, String address) {
        Bounty bounty = bountyRepository.findById(bountyVo.getId()).orElseThrow(
            () -> new BizException(NOT_FOUND_BOUNTY.getCode(), NOT_FOUND_BOUNTY.getMsg()));
        //String loginUser = UserSecurityUtils.getUserLogin().getAddress();
        if (!bounty.getCreator().equals(address)) {
            throw new BizException("2003", "not link bounty by creator");
        }
        bounty.setStreamId(bountyVo.getStreamId());
        bounty.setStreamEnd(bountyVo.getStreamStart());
        bounty.setStreamEnd(bountyVo.getStreamEnd());
        bounty.setStreamChainId(bountyVo.getStreamChainId());
        bountyRepository.save(bounty);
    }
}
