package com.salarytracker.ledger;

import com.salarytracker.ledger.LedgerModels.DeletedResource;
import com.salarytracker.ledger.LedgerModels.RecycleItem;
import com.salarytracker.ledger.LedgerModels.RecyclePurgeCommand;
import com.salarytracker.ledger.LedgerModels.ResourceType;
import com.salarytracker.platform.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class LedgerRecyclePurgeService {
    private final LedgerBookService books;
    private final LedgerTransactionService transactions;

    public LedgerRecyclePurgeService(LedgerBookService books, LedgerTransactionService transactions) {
        this.books = books;
        this.transactions = transactions;
    }

    public List<RecycleItem> all(String bookId) {
        List<RecycleItem> items = new ArrayList<>();
        int page = 1;
        while (true) {
            var result = books.recycle(bookId, page, 100);
            items.addAll(result.items());
            if (items.size() >= result.total()) return List.copyOf(items);
            page++;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<DeletedResource> purge(String bookId, List<RecyclePurgeCommand> commands) {
        if (commands == null || commands.isEmpty()) throw new IllegalArgumentException("至少选择一个回收站项目");
        Set<String> unique = new LinkedHashSet<>();
        for (RecyclePurgeCommand command : commands) {
            String key = command.type().name() + ":" + command.id();
            if (!unique.add(key)) throw new IllegalArgumentException("回收站项目不能重复");
        }

        Map<String, RecycleItem> current = new LinkedHashMap<>();
        for (RecycleItem item : all(bookId)) current.put(item.type().name() + ":" + item.id(), item);
        for (RecyclePurgeCommand command : commands) {
            RecycleItem item = current.get(command.type().name() + ":" + command.id());
            if (item == null) throw new ConflictException("回收站项目已恢复或不存在", 0L);
            if (item.revision() != command.revision()) {
                throw new ConflictException("回收站项目版本已变化", item.revision());
            }
        }

        List<DeletedResource> results = new ArrayList<>();
        for (RecyclePurgeCommand command : commands) {
            String revision = String.valueOf(command.revision());
            results.add(command.type() == ResourceType.transaction
                    ? transactions.purge(bookId, command.id(), revision)
                    : books.purgeResource(bookId, command.type().name(), command.id(), revision));
        }
        return List.copyOf(results);
    }
}
