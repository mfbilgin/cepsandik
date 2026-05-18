package com.cepsandik.bulletinboard.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = "bulletin_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulletinRecord {
    @Id
    private String id;

    @Field("election_id")
    private String electionId;

    @Field("record_type")
    private String recordType;

    @Field("tracking_code")
    private String trackingCode;

    @Field("ballot_hash")
    private String ballotHash;

    private String payload;

    @Field("previous_hash")
    private String previousHash;

    @Indexed(unique = true)
    @Field("record_hash")
    private String recordHash;

    /** İstemci idempotency anahtarı — sparse unique (publisher retry dedupe). */
    @Indexed(unique = true, sparse = true)
    @Field("idempotency_key")
    private String idempotencyKey;

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;
}
