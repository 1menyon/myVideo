package org.luckyjourney.entity.json;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;

/**
 * @description:
 * @Author: menyon
 * @CreateTime: 2023-10-29 14:00
 */
@Data
@ToString
public class TypeJson implements Serializable {
    String suggestion;
    List<CutsJson> cuts;
    List<DetailsJson> details;
}
