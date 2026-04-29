package com.interview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 提示词配置，从 application.yml 的 interview.prompts 节点读取。
 */
@ConfigurationProperties(prefix = "interview.prompts")
public class InterviewPrompts {

    private String attitudeRule = "";
    private String coordinator = "";
    private String technical = "";
    private String hr = "";
    private String closing = "";
    private String evaluation = "";
    private String resumeAnalysis = "";

    public String getAttitudeRule() { return attitudeRule; }
    public void setAttitudeRule(String value) { this.attitudeRule = value; }

    public String getCoordinator() { return coordinator; }
    public void setCoordinator(String value) { this.coordinator = value; }

    public String getTechnical() { return technical; }
    public void setTechnical(String value) { this.technical = value; }

    public String getHr() { return hr; }
    public void setHr(String value) { this.hr = value; }

    public String getClosing() { return closing; }
    public void setClosing(String value) { this.closing = value; }

    public String getEvaluation() { return evaluation; }
    public void setEvaluation(String value) { this.evaluation = value; }

    public String getResumeAnalysis() { return resumeAnalysis; }
    public void setResumeAnalysis(String value) { this.resumeAnalysis = value; }
}
