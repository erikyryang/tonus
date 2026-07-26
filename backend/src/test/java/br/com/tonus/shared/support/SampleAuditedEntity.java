package br.com.tonus.shared.support;

import br.com.tonus.shared.Audited;
import br.com.tonus.shared.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "sample_audited_entity")
@Audited
public class SampleAuditedEntity extends BaseEntity {

    private String name;

    protected SampleAuditedEntity() {
    }

    public SampleAuditedEntity(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
