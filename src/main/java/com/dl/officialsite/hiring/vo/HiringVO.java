package com.dl.officialsite.hiring.vo;

import java.util.List;
import lombok.Data;

/**
 * @ClassName HiringVO
 * @Author jackchen
 * @Date 2023/12/7 00:37
 * @Description HiringVO
 **/
@Data
public class HiringVO {

    private Long id;

    private String position;

    private String description;

    private String  location;

    private String  email;

    private List<String> mainSkills;

    private List<String> otherSkills;

    private String company;

    private String invoice;

    private int minYearlySalary;

    private int maxYearlySalary;

    private String benefits;

    private String twitter;

}
