package services;

import model.TFile;
import model.User;
import model.telegram.ContentType;
import model.telegram.api.InlineButton;
import model.telegram.api.TeleFile;
import play.Logger;
import utils.LangMap;
import utils.Strings;
import utils.TFileFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static utils.LangMap.v;
import static utils.Strings.Callback.*;
import static utils.Strings.State.*;
import static utils.TextUtils.*;

/**
 * @author Denis Danilin | denis@danilin.name
 * 16.05.2020
 * tfs ☭ sweat and blood
 */
public class HeadQuarters {
    private static final Logger.ALogger logger = Logger.of(HeadQuarters.class);

    @Inject
    private TgApi tgApi;

    @Inject
    private FsService fsService;

    @Inject
    private GUI gui;

    @Inject
    private UserService userService;


    public void accept(final User user, final TeleFile file, String input, String callbackData, final long msgId, final long callbackId) {
        try {
            if (user.getLastDialogId() > 0) {
                tgApi.deleteMessage(user.getLastDialogId(), user.getId());
                user.setLastDialogId(0);
            }

            if (!isEmpty(input) || file != null)
                tgApi.deleteMessage(msgId, user.getId());

            if (notNull(input).equals("/start")) {
                if (user.getLastMessageId() > 0) {
                    tgApi.deleteMessage(user.getLastMessageId(), user.getId());
                    user.setLastMessageId(0);
                }
                input = null;
                user.setState(View);
            }

            if (file != null) {
                fsService.upload(TFileFactory.file(file, input, user.getDirId()), user);
                input = callbackData = null;
                user.setState(View);
            }

            boolean eol = false;

            do {
                switch (user.getState()) {
                    case Gear:
                        if (!isEmpty(callbackData)) {
                            switch (callbackData) {
                                case move:
                                    tgApi.sendCallbackAnswer(LangMap.Value.None, callbackId, user);
                                    callbackData = input = null;
                                    user.setState(Move);
                                    user.setFallback(Gear);
                                    break;
                                case drop:
                                    tgApi.sendCallbackAnswer(LangMap.Value.DELETED_MANY, callbackId, user, fsService.rmSelected(user));
                                    eol = true;
                                    makeGearView(user);
                                    break;
                                case cancel:
                                    tgApi.sendCallbackAnswer(LangMap.Value.None, callbackId, user);
                                    callbackData = null;
                                    user.switchBack();
                                    break;
                                case rewind:
                                case forward:
                                    tgApi.sendCallbackAnswer(LangMap.Value.PAGE, callbackId, user, (user.getOffset() / 10) + 1);
                                    user.deltaOffset(callbackData.equals(rewind) ? -10 : 10);
                                    eol = true;
                                    makeGearView(user);
                                    break;
                                case renameEntry:
                                    tgApi.sendCallbackAnswer(LangMap.Value.None, callbackId, user);
                                    callbackData = input = null;
                                    user.setState(Rename);
                                    user.setFallback(Gear);
                                    break;
                                case checkAll:
                                    tgApi.sendCallbackAnswer(LangMap.Value.CHECK_ALL, callbackId, user, (user.getOffset() / 10) + 1);
                                    fsService.inversListSelection(user);
                                    eol = true;
                                    makeGearView(user);
                                    break;
                                default:
                                    tgApi.sendCallbackAnswer(LangMap.Value.None, callbackId, user);
                                    fsService.inversSelection(getLong(callbackData), user);
                                    eol = true;
                                    makeGearView(user);
                                    break;
                            }
                        } else if (!isEmpty(input)) {
                            user.setState(Search);
                            user.setFallback(View);
                        } else {
                            fsService.resetSelection(user);
                            eol = true;
                            makeGearView(user);
                        }
                        break;
                    case MkDir:
                        if (!isEmpty(callbackData))
                            user.switchBack();
                        else if (!isEmpty(input)) {
                            if (fsService.findAt(input, user.getDirId(), user) != null) {
                                tgApi.sendPlainText(LangMap.Value.CANT_MKDIR, user, dlg -> {
                                    user.setLastDialogId(dlg);
                                    userService.update(user);
                                }, input);
                                eol = true;
                            } else {
                                fsService.mkdir(input, user.getDirId(), user.getId());
                                input = null;
                                user.switchBack();
                            }
                        } else {
                            tgApi.ask(LangMap.Value.TYPE_FOLDER, user, dlgId -> {
                                user.setLastDialogId(dlgId);
                                userService.update(user);
                            });
                            eol = true;
                        }
                        break;
                    case MkLabel:
                        if (!isEmpty(callbackData))
                            user.switchBack();
                        else if (!isEmpty(input)) {
                            if (fsService.findAt(input, user.getDirId(), user) != null) {
                                tgApi.sendPlainText(LangMap.Value.CANT_MKLBL, user, dlgId -> {
                                    user.setLastDialogId(dlgId);
                                    userService.update(user);
                                }, input);
                                eol = true;
                            } else {
                                fsService.upload(TFileFactory.label(input, user.getDirId()), user);
                                input = null;
                                user.switchBack();
                            }
                        } else {
                            tgApi.ask(LangMap.Value.TYPE_LABEL, user, dialogId -> {
                                user.setLastDialogId(dialogId);
                                userService.update(user);
                            });
                            eol = true;
                        }
                        break;
                    case Move:
                        if (!isEmpty(callbackData)) {
                            switch (callbackData) {
                                case rewind:
                                case forward:
                                    user.deltaOffset(callbackData.equals(rewind) ? -10 : 10);
                                    tgApi.sendCallbackAnswer(LangMap.Value.PAGE, callbackId, user, (user.getOffset() / 10) + 1);
                                    eol = true;
                                    makeMoveView(user);
                                    break;
                                case put:
                                    final List<TFile> selection = fsService.getSelection(user);
                                    final Set<Long> predictors = fsService.getPredictors(user.getDirId(), user).stream().map(TFile::getId).collect(Collectors.toSet());
                                    final AtomicInteger counter = new AtomicInteger(0);
                                    selection.stream().filter(f -> !f.isDir() || !predictors.contains(f.getId())).peek(e -> counter.incrementAndGet()).forEach(f -> f.setParentId(user.getDirId()));
                                    fsService.updateMetas(selection, user);
                                    fsService.resetSelection(user);
                                    tgApi.sendCallbackAnswer(LangMap.Value.MOVED, callbackId, user, counter.get());
                                    callbackData = null;
                                    user.setState(Strings.State.View);
                                    user.setFallback(Strings.State.View);
                                    break;
                                case cancel:
                                    tgApi.sendCallbackAnswer(LangMap.Value.None, callbackId, user);
                                    callbackData = null;
                                    user.switchBack();
                                    break;
                                default:
                                    final TFile dir = callbackData.equals(goUp) ? fsService.getParentOf(user.getDirId(), user) : fsService.get(getLong(callbackData), user);
                                    tgApi.sendCallbackAnswer(LangMap.Value.CD, callbackId, user, dir.getName());
                                    user.setDirId(dir.getId());
                                    eol = true;
                                    makeMoveView(user);
                                    break;
                            }
                        } else if (!isEmpty(input)) {
                            user.setState(Search);
                            user.setFallback(View);
                        } else {
                            user.setOffset(0);
                            makeMoveView(user);
                            eol = true;
                        }
                        break;
                    case OpenFile:
                        if (!isEmpty(callbackData)) {
                            switch (callbackData) {
                                case renameEntry:
                                    tgApi.sendCallbackAnswer(LangMap.Value.None, callbackId, user);
                                    callbackData = null;
                                    user.setState(Rename);
                                    break;
                                case move:
                                    tgApi.sendCallbackAnswer(LangMap.Value.None, callbackId, user);
                                    callbackData = null;
                                    user.setState(Move);
                                    break;
                                case drop:
                                    tgApi.sendCallbackAnswer(LangMap.Value.DELETED, callbackId, user);
                                    fsService.rmSelected(user);
                                default:
                                    if (!notNull(callbackData).equals(drop))
                                        tgApi.sendCallbackAnswer(LangMap.Value.None, callbackId, user);
                                    user.switchBack();
                                    break;
                            }
                        } else if (!isEmpty(input)) {
                            user.setState(Search);
                            user.setFallback(View);
                        } else {
                            final List<TFile> selection = fsService.getSelection(user);

                            if (selection.isEmpty()) {
                                user.switchBack();
                                break;
                            }

                            final TFile entry = selection.get(0);

                            if (entry.isDir() || entry.isLabel()) {
                                user.switchBack();
                                break;
                            }

                            gui.makeFileDialog(entry, user.getId(), dlgId -> {
                                user.setLastDialogId(dlgId);
                                userService.update(user);
                            });
                            eol = true;
                        }
                        break;
                    case Rename:
                        if (!isEmpty(callbackData))
                            user.switchBack();
                        else if (!isEmpty(input)) {
                            if (fsService.findAt(input, user.getDirId(), user) != null) {
                                tgApi.sendPlainText(LangMap.Value.CANT_RN_TO, user, dlgId -> {
                                    user.setLastDialogId(dlgId);
                                    userService.update(user);
                                }, input);
                                eol = true;
                                break;
                            }

                            final List<TFile> selection = fsService.getSelection(user);

                            final TFile entry;
                            if (!selection.isEmpty() && !(entry = selection.get(0)).getName().equals(input)) {
                                entry.setName(input);
                                fsService.updateMeta(entry, user);
                            }
                            user.switchBack();
                            input = null;
                        } else {
                            final List<TFile> selection = fsService.getSelection(user);
                            final TFile entry;
                            if (selection.isEmpty() || (entry = selection.get(0)) == null) {
                                user.switchBack();
                                break;
                            }

                            tgApi.ask(LangMap.Value.TYPE_RENAME, user, dlgId -> {
                                user.setLastDialogId(dlgId);
                                userService.update(user);
                            }, entry.getName());
                            eol = true;
                        }
                        break;
                    case Search:
                        if (!isEmpty(callbackData)) {
                            switch (callbackData) {
                                case searchStateInit:
                                    tgApi.sendCallbackAnswer(LangMap.Value.None, callbackId, user);
                                    callbackData = null;
                                    break;
                                case gearStateInit:
                                    tgApi.sendCallbackAnswer(LangMap.Value.EDIT_MODE, callbackId, user);
                                    callbackData = null;
                                    user.setState(Gear);
                                    user.setFallback(Search);
                                    break;
                                case cancel:
                                    tgApi.sendCallbackAnswer(LangMap.Value.None, callbackId, user);
                                    callbackData = null;
                                    user.switchBack();
                                    break;
                                case rewind:
                                case forward:
                                    user.deltaSearchOffset(callbackData.equals(rewind) ? -10 : 10);
                                    tgApi.sendCallbackAnswer(LangMap.Value.PAGE, callbackId, user, (user.getSearchOffset() / 10) + 1);
                                    eol = true;
                                    makeSearchView(user);
                                    break;
                                default:
                                    tgApi.sendCallbackAnswer(LangMap.Value.None, callbackId, user);
                                    final TFile entry = fsService.get(getLong(callbackData), user);
                                    callbackData = null;

                                    if (entry == null || entry.isLabel())
                                        user.switchBack();
                                    else {
                                        if (entry.isDir()) {
                                            user.setDirId(entry.getId());
                                            user.setState(View);
                                        } else {
                                            fsService.setExclusiveSelected(entry.getId(), user);
                                            user.setState(OpenFile);
                                        }
                                    }
                                    break;
                            }
                        } else if (!isEmpty(input)) {
                            user.setQuery(notNull(input));
                            user.setSearchCount(fsService.findChildsByName(user.getDirId(), user.getQuery().toLowerCase(), user));
                            user.setSearchOffset(0);
                            eol = true;
                            makeSearchView(user);
                        } else {
                            tgApi.sendPlainText(LangMap.Value.TYPE_QUERY, user, dlgId -> {
                                user.setLastDialogId(dlgId);
                                userService.update(user);
                            });
                            eol = true;
                        }
                        break;
                    case SearchGear:
                        if (!isEmpty(callbackData)) {
                            switch (callbackData) {
                                case move:
                                    tgApi.sendCallbackAnswer(LangMap.Value.None, callbackId, user);
                                    user.setState(Move);
                                    callbackData = null;
                                    break;
                                case drop:
                                    tgApi.sendCallbackAnswer(LangMap.Value.DELETED_MANY, callbackId, user, fsService.rmSelected(user));
                                    eol = true;
                                    makeGearSearchView(user);
                                    break;
                                case cancel:
                                    tgApi.sendCallbackAnswer(LangMap.Value.None, callbackId, user);
                                    user.switchBack(); // should be 'Search'
                                    if (!isEmpty(user.getQuery()))
                                        input = user.getQuery();
                                    callbackData = null;
                                    break;
                                case rewind:
                                case forward:
                                    user.deltaSearchOffset(callbackData.equals(rewind) ? -10 : 10);
                                    tgApi.sendCallbackAnswer(LangMap.Value.PAGE, callbackId, user, (user.getSearchOffset() / 10) + 1);
                                    eol = true;
                                    makeGearSearchView(user);
                                    break;
                                case renameEntry:
                                    tgApi.sendCallbackAnswer(LangMap.Value.None, callbackId, user);
                                    user.setState(Rename);
                                    callbackData = null;
                                    break;
                                case checkAll:
                                    tgApi.sendCallbackAnswer(LangMap.Value.CHECK_ALL, callbackId, user, (user.getOffset() / 10) + 1);
                                    fsService.inversFoundSelection(user);
                                    eol = true;
                                    makeGearSearchView(user);
                                    break;
                                default:
                                    tgApi.sendCallbackAnswer(LangMap.Value.None, callbackId, user);
                                    fsService.inversSelection(getLong(callbackData), user);
                                    eol = true;
                                    makeGearSearchView(user);
                                    break;
                            }
                        } else if (!isEmpty(input)) {
                            user.setState(Search);
                            user.setFallback(View);
                        } else {
                            fsService.resetSelection(user);
                            makeGearSearchView(user);
                            eol = true;
                        }
                        break;
                    default:
                        if (!isEmpty(callbackData)) {
                            switch (callbackData) {
                                case goUp:
                                    final TFile dir = fsService.get(user.getDirId(), user);
                                    user.setDirId(dir.getParentId());
                                    user.setOffset(0);
                                    tgApi.sendCallbackAnswer(LangMap.Value.CD, callbackId, user, dir.getPath());
                                    eol = true;
                                    makeView(user);
                                    break;
                                case rewind:
                                case forward:
                                    user.deltaOffset(callbackData.equals(rewind) ? -10 : 10);
                                    tgApi.sendCallbackAnswer(LangMap.Value.PAGE, callbackId, user, (user.getOffset() / 10) + 1);
                                    eol = true;
                                    makeView(user);
                                    break;
                                case mkLabel:
                                    callbackData = input = null;
                                    user.setState(MkLabel);
                                    user.setFallback(View);
                                    break;
                                case searchStateInit:
                                    callbackData = input = null;
                                    user.setState(Search);
                                    user.setFallback(View);
                                    break;
                                case mkDir:
                                    callbackData = input = null;
                                    user.setState(MkDir);
                                    user.setFallback(View);
                                    break;
                                case gearStateInit:
                                    tgApi.sendCallbackAnswer(LangMap.Value.EDIT_MODE, callbackId, user);
                                    callbackData = input = null;
                                    user.setState(Gear);
                                    user.setFallback(View);
                                    break;
                                default:
                                    final TFile entry = fsService.get(getLong(callbackData), user);
                                    if (entry != null && entry.isDir()) {
                                        user.setDirId(entry.getId());
                                        user.setOffset(0);
                                        tgApi.sendCallbackAnswer(LangMap.Value.CD, callbackId, user, entry.getName());
                                        callbackData = null;
                                        break;
                                    } else if (entry != null && !entry.isLabel()) {
                                        callbackData = input = null;
                                        fsService.setExclusiveSelected(entry.getId(), user);
                                        user.setState(OpenFile);
                                        user.setFallback(View);
                                        break;
                                    } else {
                                        eol = true;
                                        makeView(user);
                                    }
                            }
                        } else if (!isEmpty(input)) {
                            user.setState(Search);
                            user.setFallback(View);
                        } else {
                            eol = true;
                            makeView(user);
                        }
                        break;
                }
            } while (!eol);

            userService.update(user);
        } catch (final Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    private void makeGearSearchView(final User user) {
        final List<TFile> scope = fsService.getFound(user);
        final List<InlineButton> upper = new ArrayList<>(0), bottom = new ArrayList<>(0);

        final long selection = scope.stream().filter(TFile::isFound).count();
        if (selection > 0) {
            if (selection == 1)
                upper.add(GUI.Buttons.renameButton);

            upper.add(new InlineButton(Strings.Uni.move + "(" + selection + ")", move));
            upper.add(new InlineButton(Strings.Uni.drop + "(" + selection + ")", drop));
        }

        upper.add(GUI.Buttons.checkAll);
        upper.add(GUI.Buttons.cancelButton);

        if (user.getSearchOffset() > 0)
            bottom.add(GUI.Buttons.rewindButton);
        if (!scope.isEmpty() && user.getSearchOffset() + 10 < scope.stream().filter(e -> e.getType() != ContentType.LABEL).count())
            bottom.add(GUI.Buttons.forwardButton);

        gui.makeGearView("_" + escapeMd(v(LangMap.Value.SEARCHED, user, user.getQuery(), user.getSearchCount())) + "_", scope, upper,
                bottom, user.getSearchOffset(), user, msgId -> {
                    if (msgId == 0 || msgId != user.getLastMessageId()) {
                        user.setLastMessageId(msgId);
                        userService.update(user);
                    }
                });
    }

    private void makeSearchView(final User user) {
        final String body;
        if (user.getSearchCount() > 0)
            body = escapeMd(v(LangMap.Value.SEARCHED, user, user.getQuery(), user.getSearchCount()));
        else
            body = escapeMd(v(LangMap.Value.NO_RESULTS, user, user.getQuery()));

        final List<TFile> scope = fsService.getFound(user);
        final List<InlineButton> upper = new ArrayList<>(0), bottom = new ArrayList<>(0);
        upper.add(GUI.Buttons.searchButton);
        upper.add(GUI.Buttons.gearButton);
        upper.add(GUI.Buttons.cancelButton);

        if (user.getSearchOffset() > 0)
            bottom.add(GUI.Buttons.rewindButton);
        if (!scope.isEmpty() && user.getSearchOffset() + 10 < scope.stream().filter(e -> e.getType() != ContentType.LABEL).count())
            bottom.add(GUI.Buttons.forwardButton);

        gui.makeMainView(body, scope, user.getSearchOffset(), upper, bottom, user.getLastMessageId(), user, msgId -> {
            if (msgId == 0 || msgId != user.getLastMessageId()) {
                user.setLastMessageId(msgId);
                userService.update(user);
            }
        });
    }

    private void makeMoveView(final User user) {
        final List<TFile> scope = fsService.listFolders(user.getDirId(), user);
        final List<InlineButton> upper = new ArrayList<>(0), bottom = new ArrayList<>(0);
        if (user.getDirId() > 1)
            upper.add(GUI.Buttons.goUpButton);
        upper.add(GUI.Buttons.putButton);
        upper.add(GUI.Buttons.cancelButton);

        if (user.getOffset() > 0)
            bottom.add(GUI.Buttons.rewindButton);
        if (!scope.isEmpty() && user.getOffset() + 10 < scope.stream().filter(e -> e.getType() != ContentType.LABEL).count())
            bottom.add(GUI.Buttons.forwardButton);

        final TFile cd = fsService.get(user.getDirId(), user);

        gui.makeMovingView(escapeMd(cd.getPath()), scope, upper, bottom, user.getOffset(), user, msgId -> {
            if (msgId == 0 || msgId != user.getLastMessageId()) {
                user.setLastMessageId(msgId);
                userService.update(user);
            }
        });
    }

    private void makeGearView(final User user) {
        final List<TFile> scope = fsService.list(user.getDirId(), user);
        final List<InlineButton> upper = new ArrayList<>(0), bottom = new ArrayList<>(0);
        final long selection = scope.stream().filter(TFile::isSelected).count();
        if (selection > 0) {
            if (selection == 1)
                upper.add(GUI.Buttons.renameButton);

            upper.add(new InlineButton(Strings.Uni.move + "(" + selection + ")", move));
            upper.add(new InlineButton(Strings.Uni.drop + "(" + selection + ")", drop));
        }
        upper.add(GUI.Buttons.checkAll);
        upper.add(GUI.Buttons.cancelButton);

        if (user.getOffset() > 0)
            bottom.add(GUI.Buttons.rewindButton);
        if (!scope.isEmpty() && user.getOffset() + 10 < scope.stream().filter(e -> e.getType() != ContentType.LABEL).count())
            bottom.add(GUI.Buttons.forwardButton);

        final TFile cd = fsService.get(user.getDirId(), user);

        gui.makeGearView(escapeMd(cd.getPath()) + (scope.isEmpty() ? "\n\n_" + escapeMd(v(LangMap.Value.NO_CONTENT, user)) + "_" : ""), scope, upper,
                bottom, user.getOffset(), user, msgId -> {
                    if (msgId == 0 || msgId != user.getLastMessageId()) {
                        user.setLastMessageId(msgId);
                        userService.update(user);
                    }
                });
    }

    private void makeView(final User user) {
        try {
            final List<TFile> scope = fsService.list(user.getDirId(), user);
            final List<InlineButton> upper = new ArrayList<>(0), bottom = new ArrayList<>(0);
            if (user.getDirId() > 1) upper.add(GUI.Buttons.goUpButton);
            upper.add(GUI.Buttons.mkLabelButton);
            upper.add(GUI.Buttons.mkDirButton);
            upper.add(GUI.Buttons.searchButton);
            upper.add(GUI.Buttons.gearButton);

            if (user.getOffset() > 0)
                bottom.add(GUI.Buttons.rewindButton);
            if (!scope.isEmpty() && user.getOffset() + 10 < scope.stream().filter(e -> e.getType() != ContentType.LABEL).count())
                bottom.add(GUI.Buttons.forwardButton);

            final TFile cd = fsService.get(user.getDirId(), user);

            gui.makeMainView(escapeMd(cd.getPath()) + (scope.isEmpty() ? "\n\n_" + escapeMd(v(LangMap.Value.NO_CONTENT, user)) + "_" : ""), scope, user.getOffset(),
                    upper, bottom, user.getLastMessageId(), user, msgId -> {
                        if (msgId == 0 || msgId != user.getLastMessageId()) {
                            user.setLastMessageId(msgId);
                            userService.update(user);
                        }
                    });
        } catch (final Exception e) {
            logger.error(e.getMessage(), e);
        }

    }
}
