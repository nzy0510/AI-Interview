# Review Rubric

Use this rubric before publishing generated atoms.

Accept an atom when:

- It tests one clear knowledge point.
- The expected answer is precise enough for an interviewer to evaluate a candidate.
- The follow-up paths include at least one deepening question and one guiding question.
- The category and difficulty match the target role.
- The atom is not a duplicate of an existing published atom.

Send back for revision when:

- It only repeats textbook headings without interview value.
- It mixes several unrelated topics in one atom.
- The answer contains unverified claims, outdated framework details, or vague wording.
- The follow-up paths reveal the full answer instead of prompting the candidate.
- It is generated from source material that looks like ads, table of contents, or page noise.

Direct-update mode:

- If the user explicitly asks to skip human review, use `AUTO_PUBLISH`.
- Still run validation and a quick search verification after import.
- Report any failed atoms or Qdrant indexing failures.
