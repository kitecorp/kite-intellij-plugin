package io.kite.intellij.formatter;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.TokenType;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.tree.IElementType;
import io.kite.intellij.parser.KiteParserDefinition;
import io.kite.intellij.psi.KiteElementTypes;
import io.kite.intellij.psi.KiteTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a formatting block in the Kite language structure.
 * Handles indentation, spacing, and child block creation.
 */
public class KiteBlock extends AbstractBlock {
    private final SpacingBuilder spacingBuilder;
    private final Indent indent;
    private final Integer alignmentPadding; // For object property alignment

    public KiteBlock(@NotNull ASTNode node,
                     @Nullable Wrap wrap,
                     @Nullable Alignment alignment,
                     @NotNull Indent indent,
                     @NotNull SpacingBuilder spacingBuilder) {
        this(node, wrap, alignment, indent, spacingBuilder, null);
    }

    public KiteBlock(@NotNull ASTNode node,
                     @Nullable Wrap wrap,
                     @Nullable Alignment alignment,
                     @NotNull Indent indent,
                     @NotNull SpacingBuilder spacingBuilder,
                     @Nullable Integer alignmentPadding) {
        super(node, wrap, alignment);
        this.spacingBuilder = spacingBuilder;
        this.indent = indent;
        this.alignmentPadding = alignmentPadding;
    }

    @Override
    protected List<Block> buildChildren() {
        IElementType nodeType = myNode.getElementType();

        // Special handling for object literals - align colons (only for multi-line)
        if (nodeType == KiteElementTypes.OBJECT_LITERAL && isMultiLine(myNode)) {
            return buildAlignedChildren(KiteTokenTypes.COLON);
        }

        // Align equals in resource/component blocks
        if (nodeType == KiteElementTypes.RESOURCE_DECLARATION ||
            nodeType == KiteElementTypes.COMPONENT_DECLARATION) {
            return buildAlignedChildren(KiteTokenTypes.ASSIGN);
        }

        // Schema declarations - skip special alignment for now, just use default
        // TODO: Add schema property alignment later

        // FILE level: handle both decorator colons and declaration equals
        if (nodeType == KiteParserDefinition.FILE) {
            return buildFileChildren();
        }

        // Default block building for other elements
        List<Block> blocks = new ArrayList<>();
        ASTNode child = myNode.getFirstChildNode();
        boolean insideBraces = false;

        while (child != null) {
            IElementType childType = child.getElementType();

            // Skip whitespace and newlines
            if (childType != TokenType.WHITE_SPACE &&
                childType != KiteTokenTypes.NL &&
                childType != KiteTokenTypes.NEWLINE &&
                child.getTextLength() > 0) {

                Indent childIndent = getChildIndentWithBraceTracking(childType, insideBraces);

                blocks.add(new KiteBlock(
                    child,
                    null,
                    null,
                    childIndent,
                    spacingBuilder
                ));

                // Update insideBraces state AFTER processing current element
                if (childType == KiteTokenTypes.LBRACE) {
                    insideBraces = true;
                } else if (childType == KiteTokenTypes.RBRACE) {
                    insideBraces = false;
                }
            }

            child = child.getTreeNext();
        }

        return blocks;
    }

    /**
     * Checks if a node spans multiple lines.
     * Used to determine if alignment should be applied.
     */
    private boolean isMultiLine(ASTNode node) {
        String text = node.getText();
        return text.contains("\n");
    }

    /**
     * Checks if a node is inside parentheses (function call/decorator arguments).
     * Used to skip alignment for object literals used as function arguments.
     */
    private boolean isInsideParentheses(ASTNode node) {
        ASTNode parent = node.getTreeParent();
        if (parent == null) return false;

        // Check if any sibling before this node is LPAREN
        ASTNode sibling = parent.getFirstChildNode();
        boolean foundLParen = false;

        while (sibling != null && sibling != node) {
            if (sibling.getElementType() == KiteTokenTypes.LPAREN) {
                foundLParen = true;
                break;
            }
            sibling = sibling.getTreeNext();
        }

        return foundLParen;
    }

    /**
     * Checks if this node contains parenthesized named arguments (identifier : value).
     * This is used for decorator arguments like @validate(regex: "...", flag: 'i')
     */
    private boolean containsParenthesizedNamedArgs() {
        // Look for pattern: LPAREN ... IDENTIFIER ... COLON ... RPAREN
        boolean hasLParen = false;
        boolean hasIdentifier = false;
        boolean hasColon = false;

        ASTNode child = myNode.getFirstChildNode();
        while (child != null) {
            IElementType type = child.getElementType();

            if (type == KiteTokenTypes.LPAREN) {
                hasLParen = true;
            } else if (hasLParen && type == KiteTokenTypes.IDENTIFIER) {
                // Check if this identifier is followed by a colon
                if (findNextToken(child, KiteTokenTypes.COLON) != null) {
                    hasIdentifier = true;
                }
            } else if (hasLParen && hasIdentifier && type == KiteTokenTypes.COLON) {
                hasColon = true;
            } else if (type == KiteTokenTypes.RPAREN && hasColon) {
                return true; // Found the pattern
            }

            child = child.getTreeNext();
        }

        return false;
    }

    /**
     * Builds children with aligned tokens (colons or equals).
     * Used for object literals, parenthesized named arguments, and block declarations.
     * Creates blocks for all tokens, calculating padding for alignment.
     * Groups consecutive similar declarations and aligns within each group.
     *
     * @param alignToken The token type to align (COLON or ASSIGN)
     */
    private List<Block> buildAlignedChildren(IElementType alignToken) {
        List<Block> blocks = new ArrayList<>();

        // First pass: identify alignment groups and their max key lengths
        List<AlignmentGroup> groups = identifyAlignmentGroups(alignToken);

        // Second pass: build blocks with appropriate padding
        ASTNode child = myNode.getFirstChildNode();
        ASTNode previousIdentifier = null;
        boolean insideBraces = false;

        while (child != null) {
            IElementType childType = child.getElementType();

            if (childType != TokenType.WHITE_SPACE &&
                childType != KiteTokenTypes.NL &&
                childType != KiteTokenTypes.NEWLINE &&
                child.getTextLength() > 0) {
                // For declaration elements, inline their children instead of creating a block for the element
                if (childType == KiteElementTypes.INPUT_DECLARATION ||
                    childType == KiteElementTypes.OUTPUT_DECLARATION ||
                    childType == KiteElementTypes.VARIABLE_DECLARATION) {

                    // Build blocks for the declaration's children
                    buildDeclarationBlocks(child, alignToken, groups, blocks, insideBraces);
                } else {
                    Indent childIndent = getChildIndentWithBraceTracking(childType, insideBraces);

                    // Track identifiers that precede the align token
                    if (childType == KiteTokenTypes.IDENTIFIER) {
                        ASTNode nextToken = findNextToken(child, alignToken);
                        if (nextToken != null) {
                            previousIdentifier = child;
                        }
                    }

                    // Calculate padding for the align token
                    Integer padding = null;
                    if (childType == alignToken && previousIdentifier != null) {
                        // Find which group this identifier belongs to
                        AlignmentGroup group = findGroupForIdentifier(groups, previousIdentifier);
                        if (group != null) {
                            int keyLength = previousIdentifier.getTextLength();
                            // For ASSIGN, always have at least 1 space; for COLON, longest key has 0 spaces
                            int extraSpace = (alignToken == KiteTokenTypes.ASSIGN) ? 1 : 0;
                            padding = group.maxKeyLength - keyLength + extraSpace;
                        }
                        previousIdentifier = null;
                    }

                    blocks.add(new KiteBlock(
                        child,
                        null,
                        null,
                        childIndent,
                        spacingBuilder,
                        padding
                    ));
                }

                // Update insideBraces state AFTER processing current element
                // So subsequent elements use the correct state
                if (childType == KiteTokenTypes.LBRACE) {
                    insideBraces = true;
                } else if (childType == KiteTokenTypes.RBRACE) {
                    insideBraces = false;
                }
            }

            child = child.getTreeNext();
        }

        return blocks;
    }

    /**
     * Builds blocks for schema property content by flattening nested structure.
     * This handles the case where schema properties are nested in intermediate nodes.
     */
    private void buildSchemaPropertyBlocks(ASTNode propertyNode, IElementType alignToken,
                                          List<AlignmentGroup> groups, List<Block> blocks) {
        // Create a tracker for this property
        SchemaPropertyTracker tracker = new SchemaPropertyTracker();

        // Flatten and process all tokens in this property
        flattenAndBuildSchemaTokens(propertyNode, alignToken, groups, blocks, tracker);
    }

    /**
     * Helper class to track state while building schema property blocks.
     */
    private static class SchemaPropertyTracker {
        ASTNode typeIdentifier = null;
        ASTNode propertyIdentifier = null;
    }

    /**
     * Recursively flattens schema property tokens and builds blocks with alignment.
     */
    private void flattenAndBuildSchemaTokens(ASTNode node, IElementType alignToken,
                                            List<AlignmentGroup> groups, List<Block> blocks,
                                            SchemaPropertyTracker tracker) {
        ASTNode child = node.getFirstChildNode();

        while (child != null) {
            IElementType childType = child.getElementType();

            if (childType == TokenType.WHITE_SPACE && child.getTextLength() > 0) {
                child = child.getTreeNext();
                continue;
            }

            // If child has children, recurse
            if (child.getFirstChildNode() != null) {
                flattenAndBuildSchemaTokens(child, alignToken, groups, blocks, tracker);
            } else if (child.getTextLength() > 0) {
                // Leaf node - process it
                Indent childIndent = getChildIndent(childType);

                // Handle identifiers - track pairs (type + property name)
                if (childType == KiteTokenTypes.IDENTIFIER) {
                    if (tracker.typeIdentifier != null) {
                        // This is the property name (second identifier in a pair)
                        tracker.propertyIdentifier = child;
                    } else {
                        // This is the type (first identifier)
                        tracker.typeIdentifier = child;
                    }
                }

                // Calculate padding for ASSIGN tokens
                Integer padding = null;
                if (childType == alignToken &&
                    tracker.typeIdentifier != null && tracker.propertyIdentifier != null) {
                    // Find which group this property identifier belongs to
                    AlignmentGroup group = findGroupForIdentifier(groups, tracker.propertyIdentifier);
                    if (group != null) {
                        // Calculate combined length: type + space + property name
                        int combinedLength = tracker.typeIdentifier.getTextLength() + 1 +
                                            tracker.propertyIdentifier.getTextLength();
                        int extraSpace = 1; // For ASSIGN, always at least 1 space
                        padding = group.maxKeyLength - combinedLength + extraSpace;
                    }
                    tracker.typeIdentifier = null;
                    tracker.propertyIdentifier = null;
                }

                // Reset identifiers at end of property
                if (childType == KiteTokenTypes.NL || childType == KiteTokenTypes.NEWLINE ||
                    childType == KiteTokenTypes.SEMICOLON) {
                    tracker.typeIdentifier = null;
                    tracker.propertyIdentifier = null;
                }

                // Create block for this token
                blocks.add(new KiteBlock(
                    child,
                    null,
                    null,
                    childIndent,
                    spacingBuilder,
                    padding
                ));
            }

            child = child.getTreeNext();
        }
    }

    /**
     * Recursively flattens all tokens in a tree into a list.
     */
    private void flattenTokens(ASTNode node, List<ASTNode> tokens) {
        ASTNode child = node.getFirstChildNode();
        while (child != null) {
            if (child.getFirstChildNode() == null) {
                // Leaf node - add to list
                tokens.add(child);
            } else {
                // Non-leaf node - recurse
                flattenTokens(child, tokens);
            }
            child = child.getTreeNext();
        }
    }

    /**
     * Builds children for FILE node, handling both decorator colons and declaration equals.
     */
    private List<Block> buildFileChildren() {
        List<Block> blocks = new ArrayList<>();

        // Identify alignment groups for both COLON and ASSIGN
        List<AlignmentGroup> colonGroups = containsParenthesizedNamedArgs() ?
            identifyAlignmentGroups(KiteTokenTypes.COLON) : new ArrayList<>();
        List<AlignmentGroup> assignGroups = identifyAlignmentGroups(KiteTokenTypes.ASSIGN);

        ASTNode child = myNode.getFirstChildNode();
        ASTNode previousIdentifier = null;

        while (child != null) {
            IElementType childType = child.getElementType();

            if (childType != TokenType.WHITE_SPACE &&
                childType != KiteTokenTypes.NL &&
                childType != KiteTokenTypes.NEWLINE &&
                child.getTextLength() > 0) {
                // For declaration elements (var/input/output), inline their children
                if (childType == KiteElementTypes.VARIABLE_DECLARATION ||
                    childType == KiteElementTypes.INPUT_DECLARATION ||
                    childType == KiteElementTypes.OUTPUT_DECLARATION) {
                    // Build blocks for the declaration's children with proper width calculation
                    // FILE-level declarations are never inside braces
                    buildDeclarationBlocks(child, KiteTokenTypes.ASSIGN, assignGroups, blocks, false);
                } else {
                    Indent childIndent = getChildIndent(childType);

                    // Track identifiers that precede align tokens
                    if (childType == KiteTokenTypes.IDENTIFIER) {
                        ASTNode nextColon = findNextToken(child, KiteTokenTypes.COLON);
                        ASTNode nextAssign = findNextToken(child, KiteTokenTypes.ASSIGN);
                        if (nextColon != null || nextAssign != null) {
                            previousIdentifier = child;
                        }
                    }

                    // Calculate padding for COLON or ASSIGN tokens
                    Integer padding = null;
                    if (previousIdentifier != null) {
                        if (childType == KiteTokenTypes.COLON) {
                            AlignmentGroup group = findGroupForIdentifier(colonGroups, previousIdentifier);
                            if (group != null) {
                                int keyLength = previousIdentifier.getTextLength();
                                padding = group.maxKeyLength - keyLength; // No extra space for colons
                            }
                            previousIdentifier = null;
                        } else if (childType == KiteTokenTypes.ASSIGN) {
                            AlignmentGroup group = findGroupForIdentifier(assignGroups, previousIdentifier);
                            if (group != null) {
                                int keyLength = previousIdentifier.getTextLength();
                                padding = group.maxKeyLength - keyLength + 1; // +1 for equals
                            }
                            previousIdentifier = null;
                        }
                    }

                    blocks.add(new KiteBlock(
                        child,
                        null,
                        null,
                        childIndent,
                        spacingBuilder,
                        padding
                    ));
                }
            }

            child = child.getTreeNext();
        }

        return blocks;
    }

    /**
     * Builds blocks for children of a declaration element (INPUT/OUTPUT/VAR).
     * Inlines the declaration's tokens rather than creating a block for the whole declaration.
     */
    private void buildDeclarationBlocks(ASTNode declarationElement,
                                       IElementType alignToken,
                                       List<AlignmentGroup> groups,
                                       List<Block> blocks,
                                       boolean insideBraces) {
        ASTNode child = declarationElement.getFirstChildNode();
        ASTNode previousIdentifier = null;

        while (child != null) {
            IElementType childType = child.getElementType();

            if (childType != TokenType.WHITE_SPACE &&
                childType != KiteTokenTypes.NL &&
                childType != KiteTokenTypes.NEWLINE &&
                child.getTextLength() > 0) {
                Indent childIndent = getChildIndentWithBraceTracking(childType, insideBraces);

                // Track identifiers that precede the align token
                if (childType == KiteTokenTypes.IDENTIFIER) {
                    ASTNode nextToken = findNextToken(child, alignToken);
                    if (nextToken != null) {
                        previousIdentifier = child;
                    }
                }

                // Calculate padding for the align token
                Integer padding = null;
                if (childType == alignToken && previousIdentifier != null) {
                    // Find which group this identifier belongs to
                    AlignmentGroup group = findGroupForIdentifier(groups, previousIdentifier);
                    if (group != null) {
                        // Calculate full declaration width for this identifier
                        int declarationWidth = calculateDeclarationWidth(declarationElement, previousIdentifier,
                                                                         declarationElement.getElementType());
                        // For ASSIGN, always have at least 1 space; for COLON, longest key has 0 spaces
                        int extraSpace = (alignToken == KiteTokenTypes.ASSIGN) ? 1 : 0;
                        padding = group.maxKeyLength - declarationWidth + extraSpace;
                    }
                    previousIdentifier = null;
                }

                blocks.add(new KiteBlock(
                    child,
                    null,
                    null,
                    childIndent,
                    spacingBuilder,
                    padding
                ));
            }

            child = child.getTreeNext();
        }
    }

    /**
     * Represents a group of consecutive declarations that should align together.
     */
    private static class AlignmentGroup {
        IElementType keywordType; // INPUT, OUTPUT, VAR, etc.
        int maxKeyLength;
        List<ASTNode> identifiers = new ArrayList<>();
    }

    /**
     * Identifies groups of consecutive similar declarations.
     * For object literals and decorator args: creates one group (align all together).
     * For block declarations: groups consecutive similar declarations, separated by blank lines.
     */
    private List<AlignmentGroup> identifyAlignmentGroups(IElementType alignToken) {
        List<AlignmentGroup> groups = new ArrayList<>();

        // For object literals and decorator args, use simple grouping (all in one group)
        if (myNode.getElementType() == KiteElementTypes.OBJECT_LITERAL ||
            containsParenthesizedNamedArgs()) {
            return identifySimpleGroup(alignToken);
        }

        // For schema declarations, use special grouping to handle properties with and without default values
        if (myNode.getElementType() == KiteElementTypes.SCHEMA_DECLARATION) {
            return identifySchemaPropertyGroups(alignToken);
        }

        // For block declarations, use smart grouping
        AlignmentGroup currentGroup = null;
        IElementType lastDeclType = null;
        boolean hasBlankLineSinceLastDecl = false;
        boolean lastWasRegularProperty = false;

        ASTNode child = myNode.getFirstChildNode();
        ASTNode prevChild = null;

        while (child != null) {
            IElementType childType = child.getElementType();

            // Detect blank lines (two consecutive newlines)
            if (childType == KiteTokenTypes.NL || childType == KiteTokenTypes.NEWLINE) {
                if (prevChild != null &&
                    (prevChild.getElementType() == KiteTokenTypes.NL ||
                     prevChild.getElementType() == KiteTokenTypes.NEWLINE)) {
                    hasBlankLineSinceLastDecl = true;
                }
            }

            // Case 1: Declaration elements (INPUT_DECLARATION, etc.)
            if (childType == KiteElementTypes.INPUT_DECLARATION ||
                childType == KiteElementTypes.OUTPUT_DECLARATION ||
                childType == KiteElementTypes.VARIABLE_DECLARATION) {

                // Start new group if type changed or blank line
                if (currentGroup == null ||
                    childType != lastDeclType ||
                    hasBlankLineSinceLastDecl ||
                    lastWasRegularProperty) {
                    currentGroup = new AlignmentGroup();
                    currentGroup.keywordType = childType;
                    groups.add(currentGroup);
                    lastDeclType = childType;
                }

                hasBlankLineSinceLastDecl = false;
                lastWasRegularProperty = false;

                // Find the identifier before the align token inside this declaration
                ASTNode identifier = findIdentifierInDeclaration(child, alignToken);
                if (identifier != null) {
                    currentGroup.identifiers.add(identifier);
                    // Calculate full declaration width: keyword + type + identifier
                    int declarationWidth = calculateDeclarationWidth(child, identifier, childType);
                    currentGroup.maxKeyLength = Math.max(currentGroup.maxKeyLength, declarationWidth);
                }
            }
            // Case 2: Regular properties (raw IDENTIFIER followed by align token)
            else if (childType == KiteTokenTypes.IDENTIFIER) {
                ASTNode nextToken = findNextToken(child, alignToken);
                if (nextToken != null) {
                    // Start new group if switching from declarations or blank line
                    if (currentGroup == null ||
                        lastDeclType != null ||
                        hasBlankLineSinceLastDecl) {
                        currentGroup = new AlignmentGroup();
                        currentGroup.keywordType = null; // Regular properties have no keyword
                        groups.add(currentGroup);
                        lastDeclType = null;
                    }

                    hasBlankLineSinceLastDecl = false;
                    lastWasRegularProperty = true;

                    currentGroup.identifiers.add(child);
                    currentGroup.maxKeyLength = Math.max(currentGroup.maxKeyLength, child.getTextLength());
                }
            }

            prevChild = child;
            child = child.getTreeNext();
        }

        return groups;
    }

    /**
     * Identifies alignment groups for schema properties.
     * Schema properties have pattern: typeIdentifier identifier [= value]
     * We want to align ALL property names (second identifier), whether or not they have default values.
     * This method traverses all tokens recursively to handle nested structures.
     */
    private List<AlignmentGroup> identifySchemaPropertyGroups(IElementType alignToken) {
        List<AlignmentGroup> groups = new ArrayList<>();
        AlignmentGroup currentGroup = new AlignmentGroup();
        groups.add(currentGroup);

        // Recursively collect all identifiers that should be aligned
        collectSchemaPropertyIdentifiers(myNode, currentGroup);

        return groups;
    }

    /**
     * Recursively collects schema property identifiers from a node and its descendants.
     * Uses flattening to ensure correct pairing across nested structures.
     * Tracks both type and property name to calculate correct alignment.
     */
    private void collectSchemaPropertyIdentifiers(ASTNode node, AlignmentGroup group) {
        // Flatten all tokens first
        List<ASTNode> allTokens = new ArrayList<>();
        flattenTokens(node, allTokens);

        ASTNode typeIdentifier = null;
        boolean inSchemaBody = false;

        for (ASTNode token : allTokens) {
            IElementType tokenType = token.getElementType();

            // Skip whitespace
            if (tokenType == TokenType.WHITE_SPACE) {
                continue;
            }

            // Track when we're inside the schema body
            if (tokenType == KiteTokenTypes.LBRACE) {
                inSchemaBody = true;
            } else if (tokenType == KiteTokenTypes.RBRACE) {
                break;
            } else if (inSchemaBody && tokenType == KiteTokenTypes.IDENTIFIER) {
                if (typeIdentifier != null) {
                    // This is the second identifier in a pair - it's the property name
                    // Add it to the group along with the type length
                    group.identifiers.add(token);

                    // Check if this identifier is followed by ASSIGN
                    boolean hasAssign = hasAssignAfter(allTokens, token);

                    // Calculate combined length: type + space + property name
                    int combinedLength = typeIdentifier.getTextLength() + 1 + token.getTextLength();

                    // For properties without ASSIGN, add 1 to ensure proper alignment
                    int effectiveLength = combinedLength + (hasAssign ? 0 : 1);
                    group.maxKeyLength = Math.max(group.maxKeyLength, effectiveLength);

                    typeIdentifier = null; // Reset for next pair
                } else {
                    // This is the first identifier - the type
                    typeIdentifier = token;
                }
            }
            // Reset typeIdentifier at end of property
            else if (tokenType == KiteTokenTypes.ASSIGN ||
                     tokenType == KiteTokenTypes.NL ||
                     tokenType == KiteTokenTypes.NEWLINE ||
                     tokenType == KiteTokenTypes.SEMICOLON) {
                typeIdentifier = null;
            }
        }
    }

    /**
     * Checks if an identifier is followed by an ASSIGN token (skipping whitespace/newlines).
     */
    private boolean hasAssignAfter(List<ASTNode> tokens, ASTNode identifier) {
        int index = tokens.indexOf(identifier);
        if (index == -1 || index >= tokens.size() - 1) {
            return false;
        }

        // Look ahead for ASSIGN, skipping whitespace and newlines
        for (int i = index + 1; i < tokens.size(); i++) {
            IElementType type = tokens.get(i).getElementType();
            if (type == KiteTokenTypes.ASSIGN) {
                return true;
            }
            if (type != TokenType.WHITE_SPACE &&
                type != KiteTokenTypes.NL &&
                type != KiteTokenTypes.NEWLINE) {
                return false; // Hit a non-whitespace, non-ASSIGN token
            }
        }
        return false;
    }

    /**
     * Checks if a token type represents a value (used in schema property parsing).
     */
    private boolean isValueToken(IElementType type) {
        return type == KiteTokenTypes.NUMBER ||
               type == KiteTokenTypes.STRING ||
               type == KiteTokenTypes.TRUE ||
               type == KiteTokenTypes.FALSE ||
               type == KiteTokenTypes.NULL ||
               type == KiteElementTypes.OBJECT_LITERAL ||
               type == KiteElementTypes.ARRAY_LITERAL;
    }

    /**
     * Creates alignment groups for identifiers, respecting parentheses boundaries.
     * For object literals: one group for all properties.
     * For decorator args (FILE level): separate group for each () pair.
     */
    private List<AlignmentGroup> identifySimpleGroup(IElementType alignToken) {
        List<AlignmentGroup> groups = new ArrayList<>();
        AlignmentGroup group = null;
        int parenDepth = 0;
        boolean inParens = false;

        ASTNode child = myNode.getFirstChildNode();
        while (child != null) {
            IElementType childType = child.getElementType();

            // Track parentheses depth
            if (childType == KiteTokenTypes.LPAREN) {
                parenDepth++;
                if (parenDepth == 1) {
                    // Start new group for this parentheses scope
                    group = new AlignmentGroup();
                    groups.add(group);
                    inParens = true;
                }
            } else if (childType == KiteTokenTypes.RPAREN) {
                parenDepth--;
                if (parenDepth == 0) {
                    inParens = false;
                }
            }
            // Add identifiers to current group
            else if (childType == KiteTokenTypes.IDENTIFIER) {
                ASTNode nextToken = findNextToken(child, alignToken);
                if (nextToken != null) {
                    // For object literals (no parens tracking), create group if needed
                    if (!inParens && group == null) {
                        group = new AlignmentGroup();
                        groups.add(group);
                    }
                    // Only add if we have a group (either in parens or object literal)
                    if (group != null) {
                        group.identifiers.add(child);
                        group.maxKeyLength = Math.max(group.maxKeyLength, child.getTextLength());
                    }
                }
            }

            child = child.getTreeNext();
        }

        return groups;
    }

    /**
     * Calculates the full width of a declaration from keyword to property identifier.
     * For INPUT/OUTPUT/VAR: keyword + type + property identifier (with spaces)
     */
    private int calculateDeclarationWidth(ASTNode declarationElement, ASTNode propertyIdentifier,
                                         IElementType declarationType) {
        int width = 0;
        ASTNode child = declarationElement.getFirstChildNode();

        while (child != null && child != propertyIdentifier) {
            IElementType type = child.getElementType();

            // Count keyword tokens
            if (type == KiteTokenTypes.INPUT || type == KiteTokenTypes.OUTPUT ||
                type == KiteTokenTypes.VAR) {
                width += child.getTextLength() + 1; // keyword + space
            }
            // Count type identifier (for input/output/var type name = value)
            else if (type == KiteTokenTypes.IDENTIFIER) {
                width += child.getTextLength() + 1; // type + space
            }

            child = child.getTreeNext();
        }

        // Add the property identifier itself
        if (propertyIdentifier != null) {
            width += propertyIdentifier.getTextLength();
        }

        return width;
    }

    /**
     * Finds the identifier before the align token inside a declaration element.
     * For INPUT/OUTPUT: skips the type, returns the property name
     * For VAR: returns the variable name
     */
    private ASTNode findIdentifierInDeclaration(ASTNode declarationElement, IElementType alignToken) {
        ASTNode child = declarationElement.getFirstChildNode();
        ASTNode lastIdentifier = null;

        while (child != null) {
            IElementType type = child.getElementType();

            // Stop at the align token
            if (type == alignToken) {
                return lastIdentifier;
            }

            // Track identifiers
            if (type == KiteTokenTypes.IDENTIFIER) {
                lastIdentifier = child;
            }

            child = child.getTreeNext();
        }

        return lastIdentifier;
    }

    /**
     * Finds the identifier before the align token, starting from a declaration keyword.
     */
    private ASTNode findIdentifierBeforeToken(ASTNode declarationKeyword, IElementType alignToken) {
        ASTNode node = declarationKeyword.getTreeNext();
        ASTNode lastIdentifier = null;

        while (node != null) {
            IElementType type = node.getElementType();

            // Stop at the align token
            if (type == alignToken) {
                return lastIdentifier;
            }

            // Track identifiers
            if (type == KiteTokenTypes.IDENTIFIER) {
                lastIdentifier = node;
            }

            // Stop at newline (end of declaration)
            if (type == KiteTokenTypes.NL || type == KiteTokenTypes.NEWLINE) {
                break;
            }

            node = node.getTreeNext();
        }

        return null;
    }

    /**
     * Finds which alignment group an identifier belongs to.
     */
    private AlignmentGroup findGroupForIdentifier(List<AlignmentGroup> groups, ASTNode identifier) {
        for (AlignmentGroup group : groups) {
            if (group.identifiers.contains(identifier)) {
                return group;
            }
        }
        return null;
    }

    /**
     * Finds the length of the longest property key.
     *
     * @param alignToken The token type that follows keys (COLON or ASSIGN)
     */
    private int findMaxKeyLength(IElementType alignToken) {
        int maxLength = 0;
        ASTNode child = myNode.getFirstChildNode();

        while (child != null) {
            if (child.getElementType() == KiteTokenTypes.IDENTIFIER) {
                ASTNode nextToken = findNextToken(child, alignToken);
                if (nextToken != null) {
                    maxLength = Math.max(maxLength, child.getTextLength());
                }
            }
            child = child.getTreeNext();
        }

        return maxLength;
    }

    /**
     * Finds the next occurrence of a specific token after a node (skipping whitespace).
     *
     * @param node       The starting node
     * @param tokenType  The token type to find (COLON or ASSIGN)
     */
    private ASTNode findNextToken(ASTNode node, IElementType tokenType) {
        ASTNode next = node.getTreeNext();
        while (next != null) {
            IElementType type = next.getElementType();
            if (type == tokenType) {
                return next;
            }
            if (type != TokenType.WHITE_SPACE &&
                type != KiteTokenTypes.NL &&
                type != KiteTokenTypes.NEWLINE) {
                return null;
            }
            next = next.getTreeNext();
        }
        return null;
    }

    /**
     * Determines the indent for a child element based on its type.
     */
    private Indent getChildIndent(IElementType childType) {
        IElementType parentType = myNode.getElementType();

        // Content inside braces should be indented
        if (isBlockElement(parentType)) {
            // Braces get no indent (align with parent)
            if (childType == KiteTokenTypes.LBRACE || childType == KiteTokenTypes.RBRACE) {
                return Indent.getNoneIndent();
            }
            // Brackets and their content get normal indent
            return Indent.getNormalIndent();
        }

        // Content inside parentheses in function declarations and calls
        if (parentType == KiteTokenTypes.LPAREN || parentType == KiteTokenTypes.RPAREN) {
            return Indent.getContinuationWithoutFirstIndent();
        }

        return Indent.getNoneIndent();
    }

    /**
     * Determines the indent for a child element, taking into account whether we're inside braces.
     * Only content between { and } should be indented.
     */
    private Indent getChildIndentWithBraceTracking(IElementType childType, boolean insideBraces) {
        IElementType parentType = myNode.getElementType();

        // Content inside braces should be indented
        if (isBlockElement(parentType)) {
            // Braces get no indent (align with parent)
            if (childType == KiteTokenTypes.LBRACE || childType == KiteTokenTypes.RBRACE) {
                return Indent.getNoneIndent();
            }
            // Only indent content that's actually inside braces
            if (insideBraces) {
                return Indent.getSpaceIndent(4);
            }
            return Indent.getNoneIndent();
        }

        // Content inside parentheses in function declarations and calls
        if (parentType == KiteTokenTypes.LPAREN || parentType == KiteTokenTypes.RPAREN) {
            return Indent.getContinuationWithoutFirstIndent();
        }

        return Indent.getNoneIndent();
    }

    /**
     * Checks if the element type is a block structure that should indent its children.
     */
    private boolean isBlockElement(IElementType type) {
        return type == KiteElementTypes.RESOURCE_DECLARATION ||
               type == KiteElementTypes.COMPONENT_DECLARATION ||
               type == KiteElementTypes.SCHEMA_DECLARATION ||
               type == KiteElementTypes.FUNCTION_DECLARATION ||
               type == KiteElementTypes.FOR_STATEMENT ||
               type == KiteElementTypes.WHILE_STATEMENT ||
               type == KiteElementTypes.OBJECT_LITERAL;
    }

    @Override
    public @Nullable Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
        // Use custom padding for aligned tokens (colons or equals)
        if (child2 instanceof KiteBlock) {
            KiteBlock kiteBlock2 = (KiteBlock) child2;
            IElementType tokenType = kiteBlock2.myNode.getElementType();

            if (kiteBlock2.alignmentPadding != null &&
                (tokenType == KiteTokenTypes.COLON || tokenType == KiteTokenTypes.ASSIGN)) {
                return Spacing.createSpacing(kiteBlock2.alignmentPadding, kiteBlock2.alignmentPadding, 0, true, 0);
            }
        }

        return spacingBuilder.getSpacing(this, child1, child2);
    }

    @Override
    public boolean isLeaf() {
        return myNode.getFirstChildNode() == null;
    }

    @Override
    public @NotNull Indent getIndent() {
        return indent;
    }

    @Override
    public @NotNull ChildAttributes getChildAttributes(int newChildIndex) {
        IElementType type = myNode.getElementType();

        // Inside block structures, use normal indentation
        if (isBlockElement(type)) {
            return new ChildAttributes(Indent.getNormalIndent(), null);
        }

        return new ChildAttributes(Indent.getNoneIndent(), null);
    }
}
