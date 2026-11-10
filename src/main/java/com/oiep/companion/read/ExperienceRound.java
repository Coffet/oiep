package com.oiep.companion.read;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * One round entry of an {@code interview-experience/v1} item.
 *
 * <p>OIEP records the round a note is about (not a full multi-round loop), so the adapter
 * always emits exactly one entry: {@code index=1}, the mapped round type, {@code format=UNKNOWN}
 * and the submitted question-type tags as topics. Optional keys are omitted when absent. No
 * round notes are emitted: the free-text account lives in {@code overall_experience}.</p>
 *
 * @param index  1-based round index (always 1 for OIEP-derived items)
 * @param type   mapped interview round type, e.g. {@code TECHNICAL}
 * @param format interview format, always {@code UNKNOWN} (OIEP does not collect it)
 * @param topics question-type tags from the OIEP form ({@code question_types})
 * @param notes  unused for OIEP-derived items (always null)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExperienceRound(int index, String type, String format, List<String> topics, String notes) {
}
